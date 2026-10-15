import { Router } from "express";

export const healthRouter = Router();

healthRouter.get("/", (_req, res) => {
  res.status(200).json({
    status: "ok",
    service: "scritto-api",
    timestamp: new Date().toISOString()
  });
});
