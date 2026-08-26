CREATE TABLE "analysis_runs" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"status" text DEFAULT 'running' NOT NULL,
	"notification_count" smallint DEFAULT 0 NOT NULL,
	"reminder_count" smallint DEFAULT 0 NOT NULL,
	"update_count" smallint DEFAULT 0 NOT NULL,
	"error" text,
	"result" jsonb,
	"started_at" timestamp with time zone DEFAULT now() NOT NULL,
	"completed_at" timestamp with time zone
);
--> statement-breakpoint
ALTER TABLE "analysis_runs" ADD CONSTRAINT "analysis_runs_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "analysis_runs_user_started_idx" ON "analysis_runs" USING btree ("user_id","started_at");