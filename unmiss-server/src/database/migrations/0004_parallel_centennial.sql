ALTER TABLE "users" ALTER COLUMN "analysis_last_run_at" SET DEFAULT now();--> statement-breakpoint
ALTER TABLE "users" ALTER COLUMN "analysis_last_run_at" SET NOT NULL;