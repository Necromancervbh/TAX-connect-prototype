CREATE TABLE IF NOT EXISTS negotiations (
  id UUID PRIMARY KEY,
  client_id TEXT NOT NULL,
  ca_id TEXT NOT NULL,
  status TEXT NOT NULL,
  current_turn TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  locked_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS proposals (
  id UUID PRIMARY KEY,
  negotiation_id UUID NOT NULL REFERENCES negotiations(id),
  author_id TEXT NOT NULL,
  author_role TEXT NOT NULL,
  amount_inr NUMERIC(12, 2) NOT NULL,
  scope_text TEXT NOT NULL,
  deliverables_json JSONB NOT NULL,
  acceptance_json JSONB NOT NULL,
  proposal_type TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS agreements (
  id UUID PRIMARY KEY,
  agreement_id TEXT UNIQUE NOT NULL,
  negotiation_id UUID NOT NULL REFERENCES negotiations(id),
  final_price NUMERIC(12, 2) NOT NULL,
  scope_text TEXT NOT NULL,
  deliverables_json JSONB NOT NULL,
  acceptance_json JSONB NOT NULL,
  installment_advance NUMERIC(12, 2) NOT NULL,
  installment_balance NUMERIC(12, 2) NOT NULL,
  status TEXT NOT NULL,
  qr_payload TEXT NOT NULL,
  unsigned_pdf_url TEXT,
  signed_pdf_url TEXT,
  signed_checksum TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_ledger (
  id BIGSERIAL PRIMARY KEY,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  action TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
  id UUID PRIMARY KEY,
  agreement_id UUID NOT NULL REFERENCES agreements(id),
  installment_type TEXT NOT NULL,
  amount NUMERIC(12, 2) NOT NULL,
  status TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  gateway_reference TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS invoices (
  id UUID PRIMARY KEY,
  payment_id UUID NOT NULL REFERENCES payments(id),
  gst_json JSONB NOT NULL,
  pdf_url TEXT,
  checksum TEXT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS disputes (
  id UUID PRIMARY KEY,
  agreement_id UUID NOT NULL REFERENCES agreements(id),
  ticket_id TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS escrow_accounts (
  id UUID PRIMARY KEY,
  agreement_id UUID NOT NULL REFERENCES agreements(id),
  status TEXT NOT NULL,
  balance NUMERIC(12, 2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS reminders (
  id UUID PRIMARY KEY,
  agreement_id UUID NOT NULL REFERENCES agreements(id),
  installment_type TEXT NOT NULL,
  remind_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS milestone_events (
  id UUID PRIMARY KEY,
  agreement_id UUID NOT NULL REFERENCES agreements(id),
  milestone TEXT NOT NULL,
  status TEXT NOT NULL,
  evidence_url TEXT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
  id UUID PRIMARY KEY,
  user_id TEXT NOT NULL,
  channel TEXT NOT NULL,
  payload JSONB NOT NULL,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
