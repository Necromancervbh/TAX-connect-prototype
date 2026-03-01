const express = require("express");
const { query } = require("../db");
const { newId } = require("../utils/ids");
const { appendLedger } = require("../utils/ledger");
const { paymentSchema } = require("../utils/validation");
const { canReleaseEscrow, canPayBalance } = require("../utils/escrow");
const { fpeEncryptDigits } = require("../utils/security");

const router = express.Router();

function now() {
  return new Date().toISOString();
}

function getIdempotencyKey(req) {
  return req.header("Idempotency-Key") || req.header("idempotency-key");
}

async function latestMilestone(agreementId, milestone) {
  const result = await query(
    "SELECT status FROM milestone_events WHERE agreement_id = $1 AND milestone = $2 ORDER BY created_at DESC LIMIT 1",
    [agreementId, milestone]
  );
  if (!result.rowCount) {
    return null;
  }
  return result.rows[0].status;
}

async function latestDisputeStatus(agreementId) {
  const result = await query(
    "SELECT status FROM disputes WHERE agreement_id = $1 ORDER BY created_at DESC LIMIT 1",
    [agreementId]
  );
  if (!result.rowCount) {
    return "NONE";
  }
  return result.rows[0].status;
}

async function enqueueWebhookNotification({ payment, url, body, errorMessage }) {
  const id = newId();
  const createdAt = now();
  const payload = {
    paymentId: payment.id,
    agreementId: payment.agreement_id,
    webhookUrl: url,
    body,
    attempts: 1,
    lastError: errorMessage,
    lastAttemptAt: createdAt
  };
  await query(
    "INSERT INTO notifications (id, user_id, channel, payload, status, created_at) VALUES ($1,$2,$3,$4,$5,$6)",
    [
      id,
      payment.agreement_id,
      "ACCOUNTING_WEBHOOK",
      JSON.stringify(payload),
      "PENDING",
      createdAt
    ]
  );
  await appendLedger({
    entityType: "payment",
    entityId: payment.id,
    action: "webhook_queued",
    actorId: "system",
    actorRole: "SYSTEM",
    payload
  });
}

async function attemptWebhookDispatch(payload) {
  const url = payload.webhookUrl || process.env.ACCOUNTING_WEBHOOK_URL;
  if (!url) {
    throw new Error("Missing accounting webhook url");
  }
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload.body)
  });
  if (!response.ok) {
    throw new Error(`Webhook status ${response.status}`);
  }
}

router.post("/webhooks/retry", async (req, res) => {
  const actor = req.actor;
  const limit = Math.min(Number(req.query.limit) || 25, 100);
  const result = await query(
    "SELECT * FROM notifications WHERE channel = $1 AND status IN ('PENDING','FAILED') ORDER BY created_at ASC LIMIT $2",
    ["ACCOUNTING_WEBHOOK", limit]
  );
  let sent = 0;
  let failed = 0;
  for (const notification of result.rows) {
    const payload =
      typeof notification.payload === "string"
        ? JSON.parse(notification.payload)
        : notification.payload;
    const updatedPayload = {
      ...payload,
      attempts: Number(payload.attempts || 0) + 1,
      lastAttemptAt: now()
    };
    try {
      await attemptWebhookDispatch(updatedPayload);
      await query("UPDATE notifications SET status = $1, payload = $2 WHERE id = $3", [
        "SENT",
        JSON.stringify(updatedPayload),
        notification.id
      ]);
      await appendLedger({
        entityType: "payment",
        entityId: payload.paymentId || notification.user_id,
        action: "webhook_retry_succeeded",
        actorId: actor.id,
        actorRole: actor.role,
        payload: updatedPayload
      });
      sent += 1;
    } catch (error) {
      const failedPayload = {
        ...updatedPayload,
        lastError: error?.message ? String(error.message) : "Dispatch failed"
      };
      await query("UPDATE notifications SET status = $1, payload = $2 WHERE id = $3", [
        "FAILED",
        JSON.stringify(failedPayload),
        notification.id
      ]);
      await appendLedger({
        entityType: "payment",
        entityId: payload.paymentId || notification.user_id,
        action: "webhook_retry_failed",
        actorId: actor.id,
        actorRole: actor.role,
        payload: failedPayload
      });
      failed += 1;
    }
  }
  res.json({ processed: result.rowCount, sent, failed });
});

router.post("/:id/payments/advance", async (req, res) => {
  const actor = req.actor;
  const key = getIdempotencyKey(req);
  if (!key) {
    return res.status(400).json({ error: "Idempotency-Key required" });
  }
  const agreementResult = await query("SELECT * FROM agreements WHERE id = $1", [
    req.params.id
  ]);
  if (!agreementResult.rowCount) {
    return res.status(404).json({ error: "Agreement not found" });
  }
  const agreement = agreementResult.rows[0];
  if (agreement.status !== "SIGNED") {
    return res.status(402).json({ error: "Agreement not signed" });
  }
  const payload = paymentSchema.safeParse(req.body);
  if (!payload.success) {
    return res.status(400).json({ error: "Invalid payment payload" });
  }
  if (payload.data.paymentMethod === "CARD" && !payload.data.enforce3ds) {
    return res.status(400).json({ error: "3DS enforcement required" });
  }
  const existing = await query(
    "SELECT * FROM payments WHERE idempotency_key = $1",
    [key]
  );
  if (existing.rowCount) {
    return res.json(existing.rows[0]);
  }
  const amount = Number(agreement.installment_advance);
  if (amount !== payload.data.amountInr) {
    return res.status(400).json({ error: "Advance amount mismatch" });
  }
  const paymentId = newId();
  const createdAt = now();
  await query(
    "INSERT INTO payments (id, agreement_id, installment_type, amount, status, idempotency_key, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$7)",
    [paymentId, agreement.id, "ADVANCE", amount, "INITIATED", key, createdAt]
  );
  await query(
    "INSERT INTO escrow_accounts (id, agreement_id, status, balance, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$5)",
    [newId(), agreement.id, "HELD", amount, createdAt]
  );
  await appendLedger({
    entityType: "payment",
    entityId: paymentId,
    action: "advance_initiated",
    actorId: actor.id,
    actorRole: actor.role,
    payload: {
      amount,
      method: payload.data.paymentMethod
    }
  });
  const invoiceId = await createInvoice(paymentId, payload.data);
  res.status(201).json({ paymentId, status: "INITIATED", invoiceId });
});

router.post("/:id/payments/balance", async (req, res) => {
  const actor = req.actor;
  const key = getIdempotencyKey(req);
  if (!key) {
    return res.status(400).json({ error: "Idempotency-Key required" });
  }
  const agreementResult = await query("SELECT * FROM agreements WHERE id = $1", [
    req.params.id
  ]);
  if (!agreementResult.rowCount) {
    return res.status(404).json({ error: "Agreement not found" });
  }
  const agreement = agreementResult.rows[0];
  const disputeStatus = await latestDisputeStatus(agreement.id);
  const completionStatus = await latestMilestone(agreement.id, "WORK_COMPLETED");
  if (!canPayBalance({ completionStatus, disputeStatus })) {
    return res.status(402).json({ error: "Escrow conditions not met" });
  }
  const payload = paymentSchema.safeParse(req.body);
  if (!payload.success) {
    return res.status(400).json({ error: "Invalid payment payload" });
  }
  if (payload.data.paymentMethod === "CARD" && !payload.data.enforce3ds) {
    return res.status(400).json({ error: "3DS enforcement required" });
  }
  const existing = await query(
    "SELECT * FROM payments WHERE idempotency_key = $1",
    [key]
  );
  if (existing.rowCount) {
    return res.json(existing.rows[0]);
  }
  const amount = Number(agreement.installment_balance);
  if (amount !== payload.data.amountInr) {
    return res.status(400).json({ error: "Balance amount mismatch" });
  }
  const paymentId = newId();
  const createdAt = now();
  await query(
    "INSERT INTO payments (id, agreement_id, installment_type, amount, status, idempotency_key, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$7)",
    [paymentId, agreement.id, "BALANCE", amount, "INITIATED", key, createdAt]
  );
  await appendLedger({
    entityType: "payment",
    entityId: paymentId,
    action: "balance_initiated",
    actorId: actor.id,
    actorRole: actor.role,
    payload: {
      amount,
      method: payload.data.paymentMethod
    }
  });
  const invoiceId = await createInvoice(paymentId, payload.data);
  res.status(201).json({ paymentId, status: "INITIATED", invoiceId });
});

router.post("/:id/escrow/release", async (req, res) => {
  const actor = req.actor;
  const escrowResult = await query(
    "SELECT status FROM escrow_accounts WHERE agreement_id = $1 ORDER BY created_at DESC LIMIT 1",
    [req.params.id]
  );
  if (escrowResult.rowCount && escrowResult.rows[0].status === "RELEASED") {
    return res.json({ status: "RELEASED" });
  }
  const disputeStatus = await latestDisputeStatus(req.params.id);
  const milestoneStatus = await latestMilestone(req.params.id, "WORK_COMMENCED");
  if (!canReleaseEscrow({ milestoneStatus, disputeStatus })) {
    return res.status(409).json({ error: "Escrow release conditions not met" });
  }
  await query(
    "UPDATE escrow_accounts SET status = $1, updated_at = $2 WHERE agreement_id = $3",
    ["RELEASED", now(), req.params.id]
  );
  await appendLedger({
    entityType: "escrow",
    entityId: req.params.id,
    action: "escrow_released",
    actorId: actor.id,
    actorRole: actor.role,
    payload: { milestoneStatus }
  });
  res.json({ status: "RELEASED" });
});

router.post("/:agreementId/payments/:paymentId/confirm", async (req, res) => {
  const actor = req.actor;
  const paymentResult = await query("SELECT * FROM payments WHERE id = $1", [
    req.params.paymentId
  ]);
  if (!paymentResult.rowCount) {
    return res.status(404).json({ error: "Payment not found" });
  }
  const payment = paymentResult.rows[0];
  if (payment.agreement_id !== req.params.agreementId) {
    return res.status(409).json({ error: "Agreement mismatch" });
  }
  if (payment.status === "PAID") {
    return res.json({ status: "PAID" });
  }
  const updatedAt = now();
  await query("UPDATE payments SET status = $1, updated_at = $2 WHERE id = $3", [
    "PAID",
    updatedAt,
    payment.id
  ]);
  await appendLedger({
    entityType: "payment",
    entityId: payment.id,
    action: "payment_confirmed",
    actorId: actor.id,
    actorRole: actor.role,
    payload: {
      installmentType: payment.installment_type,
      amount: Number(payment.amount)
    }
  });
  await dispatchAccountingWebhook(payment);
  res.json({ status: "PAID" });
});

async function createInvoice(paymentId, data) {
  const invoiceId = newId();
  const gstRate = data.gstRate ?? 0.18;
  const amount = data.amountInr;
  const taxValue = Number((amount * gstRate).toFixed(2));
  const gstSplit =
    data.gstType === "IGST"
      ? { igst: taxValue }
      : { cgst: Number((taxValue / 2).toFixed(2)), sgst: Number((taxValue / 2).toFixed(2)) };
  const gstJson = {
    format: "GST-INV-01",
    rate: gstRate,
    amount,
    hsnSac: data.hsnSac,
    gstinCa: fpeEncryptDigits(data.gstinCa, paymentId),
    gstinClient: data.gstinClient ? fpeEncryptDigits(data.gstinClient, paymentId) : null,
    ...gstSplit
  };
  await query(
    "INSERT INTO invoices (id, payment_id, gst_json, created_at) VALUES ($1,$2,$3,$4)",
    [invoiceId, paymentId, JSON.stringify(gstJson), now()]
  );
  return invoiceId;
}

async function dispatchAccountingWebhook(payment) {
  const url = process.env.ACCOUNTING_WEBHOOK_URL;
  if (!url) {
    return;
  }
  const body = {
    paymentId: payment.id,
    agreementId: payment.agreement_id,
    installmentType: payment.installment_type,
    amount: Number(payment.amount),
    status: payment.status
  };
  try {
    await attemptWebhookDispatch({ webhookUrl: url, body });
  } catch (error) {
    await enqueueWebhookNotification({
      payment,
      url,
      body,
      errorMessage: error?.message ? String(error.message) : "Dispatch failed"
    });
  }
}

module.exports = router;
