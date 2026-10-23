import express from "express";
import helmet from "helmet";
import { healthRouter } from "./routes/health.routes.js";
import { supabaseHealthRouter } from "./routes/supabase-health.routes.js";
import { databaseHealthRouter } from "./routes/database-health.routes.js";
import { authRouter, adminRouter } from "./routes/auth.routes.js";
import { devicesRouter } from "./routes/devices.routes.js";

export const app = express();

app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "100kb" }));

app.use("/api/v1/health", healthRouter);
app.use("/api/v1/health/supabase", supabaseHealthRouter);
app.use("/api/v1/health/database", databaseHealthRouter);
app.use("/api/v1/auth", authRouter);
app.use("/api/v1/devices", devicesRouter);
app.use("/api/v1/admin", adminRouter);

app.use((_req, res) => {
  res.status(404).json({
    error: {
      code: "NOT_FOUND",
      message: "The requested resource was not found."
    }
  });
});
