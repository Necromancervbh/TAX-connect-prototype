const fs = require("fs");
const path = require("path");
const { pool } = require("./db");

const schemaPath = path.join(__dirname, "schema.sql");
const schemaSql = fs.readFileSync(schemaPath, "utf8");

pool
  .query(schemaSql)
  .then(() => {
    process.stdout.write("Migration completed\n");
    return pool.end();
  })
  .catch((error) => {
    process.stderr.write(String(error) + "\n");
    process.exitCode = 1;
    return pool.end();
  });
