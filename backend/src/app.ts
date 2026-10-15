import express from "express";
import helmet from "helmet";
import { healthRouter } from "./routes/health.routes.js";
import { supabaseHealthRouter } from "./routes/supabase-health.routes.js";

export const app = express();

app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "100kb" }));

app.use("/api/v1/health", healthRouter);
app.use("/api/v1/health/supabase", supabaseHealthRouter);

app.use((_req, res) => {
  res.status(404).json({
    error: {
      code: "NOT_FOUND",
      message: "The requested resource was not found."
    }
  });
});
