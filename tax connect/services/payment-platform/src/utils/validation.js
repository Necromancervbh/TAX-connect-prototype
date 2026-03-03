const { z } = require("zod");

const negotiationSchema = z.object({
  clientId: z.string().min(1),
  caId: z.string().min(1),
  scopeText: z.string().min(1),
  deliverables: z.array(z.string().min(1)).min(1),
  acceptanceCriteria: z.array(z.string().min(1)).min(1)
});

const proposalSchema = z.object({
  amountInr: z.number().positive(),
  scopeText: z.string().min(1),
  deliverables: z.array(z.string().min(1)).min(1),
  acceptanceCriteria: z.array(z.string().min(1)).min(1),
  proposalType: z.enum(["PROPOSAL", "COUNTER", "FINAL"])
});

const paymentSchema = z.object({
  amountInr: z.number().positive(),
  gstRate: z.number().nonnegative().max(1).default(0.18),
  gstType: z.enum(["CGST_SGST", "IGST"]).default("CGST_SGST"),
  gstinClient: z.string().optional(),
  gstinCa: z.string().min(1),
  hsnSac: z.string().min(1),
  paymentMethod: z.enum(["UPI", "NETBANKING", "NEFT_RTGS", "CARD"]),
  enforce3ds: z.boolean().default(true)
});

const disputeSchema = z.object({
  reason: z.string().min(5)
});

module.exports = {
  negotiationSchema,
  proposalSchema,
  paymentSchema,
  disputeSchema
};
