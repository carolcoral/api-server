const fs = require("fs");
const buf = fs.readFileSync("/workspace/backend/data/api-server.db");
const str = buf.toString("utf8");

// Look around offset 11682 where we found https://
const idx = 11682;
const context = str.slice(Math.max(0, idx - 200), idx + 400).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
console.log("Context around offset 11682:");
console.log(context);
console.log("\n---\n");

// Also look at TDP provider area
const tdpIdx = str.indexOf("TDP");
const tdpContext = str.slice(Math.max(0, tdpIdx - 200), tdpIdx + 400).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
console.log("Context around TDP (offset", tdpIdx, "):");
console.log(tdpContext);
