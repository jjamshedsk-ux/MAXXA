const express = require("express");
const { GoogleGenAI } = require("@google/genai");

const app = express();
app.use(express.json({ limit: "1mb" }));

const PORT = process.env.PORT || 3000;
const API_KEY = process.env.GEMINI_API_KEY;

app.get("/", (req, res) => {
  res.json({
    app: "MAXXA AI Backend",
    status: "online"
  });
});

app.get("/health", (req, res) => {
  res.json({ status: "ok" });
});

app.post("/chat", async (req, res) => {
  try {
    if (!API_KEY) {
      return res.status(500).json({
        error: "Backend API key is not configured."
      });
    }

    const message = req.body?.message;

    if (typeof message !== "string" || !message.trim()) {
      return res.status(400).json({
        error: "Please provide a message."
      });
    }

    const ai = new GoogleGenAI({ apiKey: API_KEY });

    const result = await ai.models.generateContent({
      model: "gemini-2.5-flash",
      contents: message.trim()
    });

    res.json({
      reply: result.text || "I couldn't generate a response."
    });
  } catch (error) {
    console.error("Gemini request failed:", error.message);
    res.status(502).json({
      error: "AI request failed. Please try again."
    });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`MAXXA AI backend listening on port ${PORT}`);
});
