const express = require("express");
const helmet = require("helmet");
const negotiations = require("./routes/negotiations");
const agreements = require("./routes/agreements");
const payments = require("./routes/payments");
const disputes = require("./routes/disputes");
const audit = require("./routes/audit");

const app = express();
const port = process.env.PORT || 8080;

app.use(
  helmet({
    hsts: { maxAge: 31536000, includeSubDomains: true, preload: true },
    contentSecurityPolicy: {
      useDefaults: true,
      directives: {
        "default-src": ["'self'"],
        "frame-ancestors": ["'none'"]
      }
    }
  })
);
app.use(express.json({ limit: "1mb" }));

app.get("/health", (req, res) => {
  res.json({ status: "ok" });
});

app.use((req, res, next) => {
  const userId = req.header("x-user-id");
  const role = req.header("x-role");
  if (!userId || !role) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  req.actor = { id: userId, role };
  return next();
});

app.use("/negotiations", negotiations);
app.use("/agreements", agreements);
app.use("/agreements", payments);
app.use("/agreements", disputes);
app.use("/audit", audit);

app.use((err, req, res, next) => {
  res.status(500).json({ error: "Unexpected error" });
});

app.listen(port, () => {
  process.stdout.write(`payment-platform listening on ${port}\n`);
});
