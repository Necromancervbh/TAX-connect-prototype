const fs = require("fs");
const path = require("path");
const { S3Client, PutObjectCommand } = require("@aws-sdk/client-s3");
const { sha256 } = require("../utils/ids");

const bucket = process.env.S3_BUCKET;
const region = process.env.AWS_REGION || "ap-south-1";
const storageDir = process.env.AGREEMENT_STORAGE_DIR || path.join(process.cwd(), "storage");

let client = null;
if (bucket) {
  client = new S3Client({ region });
}

async function uploadAgreement(agreementId, buffer) {
  if (client && bucket) {
    const key = `agreements/${agreementId}.pdf`;
    await client.send(
      new PutObjectCommand({
        Bucket: bucket,
        Key: key,
        Body: buffer,
        ContentType: "application/pdf",
        Metadata: {
          checksum: sha256(buffer)
        }
      })
    );
    return { url: `s3://${bucket}/${key}` };
  }
  fs.mkdirSync(storageDir, { recursive: true });
  const filePath = path.join(storageDir, `${agreementId}.pdf`);
  fs.writeFileSync(filePath, buffer);
  return { url: filePath };
}

async function uploadSignedAgreement(agreementId, buffer) {
  if (client && bucket) {
    const key = `agreements/${agreementId}-signed.pdf`;
    await client.send(
      new PutObjectCommand({
        Bucket: bucket,
        Key: key,
        Body: buffer,
        ContentType: "application/pdf",
        Metadata: {
          checksum: sha256(buffer)
        }
      })
    );
    return { url: `s3://${bucket}/${key}`, checksum: sha256(buffer) };
  }
  fs.mkdirSync(storageDir, { recursive: true });
  const filePath = path.join(storageDir, `${agreementId}-signed.pdf`);
  fs.writeFileSync(filePath, buffer);
  return { url: filePath, checksum: sha256(buffer) };
}

module.exports = {
  uploadAgreement,
  uploadSignedAgreement
};
