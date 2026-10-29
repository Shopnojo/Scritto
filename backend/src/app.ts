import express from "express";
import helmet from "helmet";
import { healthRouter } from "./routes/health.routes.js";
import { supabaseHealthRouter } from "./routes/supabase-health.routes.js";
import { databaseHealthRouter } from "./routes/database-health.routes.js";
import { authRouter, adminRouter } from "./routes/auth.routes.js";
import { devicesRouter } from "./routes/devices.routes.js";
import { adminDataRouter, configRouter, notificationsRouter } from "./routes/admin-data.routes.js";

export const app = express();

app.disable("x-powered-by");
app.use(helmet());

// Local Next.js development origins are allowed by default. Production must set
// FRONTEND_ORIGIN to the exact deployed frontend origin.
const allowedOrigins = new Set(
  (process.env.FRONTEND_ORIGIN ?? "http://localhost:3000,http://127.0.0.1:3000")
    .split(",").map((origin) => origin.trim()).filter(Boolean)
);
app.use((req, res, next) => {
  const origin = req.get("origin");
  if (origin && allowedOrigins.has(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
    res.setHeader("Access-Control-Allow-Credentials", "true");
  }
  if (req.method === "OPTIONS") {
    res.status(origin && allowedOrigins.has(origin) ? 204 : 403).end();
    return;
  }
  next();
});

app.use(express.json({ limit: "100kb" }));

app.use("/api/v1/health", healthRouter);
app.use("/api/v1/health/supabase", supabaseHealthRouter);
app.use("/api/v1/health/database", databaseHealthRouter);
app.use("/api/v1/auth", authRouter);
app.use("/api/v1/devices", devicesRouter);
app.use("/api/v1/admin", adminDataRouter);
app.use("/api/v1/admin", adminRouter);
app.use("/api/v1/admin/notifications", notificationsRouter);
app.use("/api/v1/config", configRouter);

app.use((_req, res) => {
  res.status(404).json({
    error: {
      code: "NOT_FOUND",
      message: "The requested resource was not found."
    }
  });
});
