const fs = require("fs");
const buf = fs.readFileSync("/workspace/backend/data/api-server.db");
const str = buf.toString("utf8");

const keywords = ["TDP", "OPENAI", "VLLM", "OLLAMA", "CUSTOM", "http://", "https://"];
keywords.forEach(kw => {
    let idx = str.indexOf(kw);
    if (idx >= 0) {
        const context = str.slice(Math.max(0, idx - 50), idx + 100).replace(/[\x00-\x08\x0b-\x1f]/g, " ");
        console.log("Found " + kw + " at offset", idx);
        console.log("  Context:", context.trim().slice(0, 120));
        console.log("");
    }
});

console.log("api_key found at:", str.indexOf("api_key"));
console.log("t_ai_provider found at:", str.indexOf("t_ai_provider"));
