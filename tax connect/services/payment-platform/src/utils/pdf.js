const PDFDocument = require("pdfkit");
const QRCode = require("qrcode");
const { sha256 } = require("./ids");

async function generateAgreementPdf({
  agreementId,
  finalPrice,
  scopeText,
  deliverables,
  acceptanceCriteria,
  installmentAdvance,
  installmentBalance
}) {
  const doc = new PDFDocument({ size: "A4", margin: 48 });
  const chunks = [];
  doc.on("data", (chunk) => chunks.push(chunk));

  doc.fontSize(18).text("Service Agreement", { align: "center" });
  doc.moveDown();
  doc.fontSize(12).text(`Agreement ID: ${agreementId}`);
  doc.text(`Final Price (INR): ${finalPrice.toFixed(2)}`);
  doc.moveDown();
  doc.fontSize(12).text("Scope of Work");
  doc.fontSize(10).text(scopeText);
  doc.moveDown();
  doc.fontSize(12).text("Deliverables");
  deliverables.forEach((item, index) => {
    doc.fontSize(10).text(`${index + 1}. ${item}`);
  });
  doc.moveDown();
  doc.fontSize(12).text("Acceptance Criteria");
  acceptanceCriteria.forEach((item, index) => {
    doc.fontSize(10).text(`${index + 1}. ${item}`);
  });
  doc.moveDown();
  doc.fontSize(12).text("Installment Schedule");
  doc.fontSize(10).text(`Advance (50%): INR ${installmentAdvance.toFixed(2)}`);
  doc.fontSize(10).text(`Balance (50%): INR ${installmentBalance.toFixed(2)}`);
  doc.moveDown();

  const qrPayload = `agreement:${agreementId}`;
  const qrDataUrl = await QRCode.toDataURL(qrPayload, { margin: 1, width: 120 });
  const qrImage = Buffer.from(qrDataUrl.split(",")[1], "base64");
  doc.image(qrImage, { width: 120, align: "left" });

  doc.end();

  const buffer = await new Promise((resolve) => {
    doc.on("end", () => resolve(Buffer.concat(chunks)));
  });

  return {
    buffer,
    checksum: sha256(buffer),
    qrPayload
  };
}

module.exports = {
  generateAgreementPdf
};
