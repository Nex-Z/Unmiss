ALTER TABLE "notifications" ADD COLUMN "timezone" text DEFAULT 'UTC' NOT NULL;--> statement-breakpoint
ALTER TABLE "notifications" ADD COLUMN "agent_processing_at" timestamp with time zone;