ALTER TABLE "users" ADD COLUMN "analysis_times" jsonb DEFAULT '["22:00"]'::jsonb NOT NULL;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "analysis_timezone" text DEFAULT 'UTC' NOT NULL;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "analysis_last_run_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "analysis_processing_at" timestamp with time zone;