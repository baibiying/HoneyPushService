CREATE TABLE IF NOT EXISTS users (
    id varchar(128) PRIMARY KEY NOT NULL,
    email varchar(256),
    password_hash text,
    auth_provider varchar(32) DEFAULT 'password' NOT NULL,
    email_verified_at timestamp,
    name text,
    avatar_url text,
    created_at timestamp DEFAULT now() NOT NULL,
    updated_at timestamp DEFAULT now() NOT NULL,
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id serial PRIMARY KEY NOT NULL,
    user_id varchar(128) NOT NULL,
    session_token_hash text NOT NULL,
    expires_at timestamp NOT NULL,
    created_at timestamp DEFAULT now() NOT NULL,
    CONSTRAINT auth_sessions_session_token_hash_unique UNIQUE (session_token_hash)
);

CREATE TABLE IF NOT EXISTS tasks (
    id serial PRIMARY KEY NOT NULL,
    user_id text NOT NULL,
    text text NOT NULL,
    duration_minutes integer DEFAULT 25 NOT NULL,
    category varchar(64) DEFAULT 'import-urgent' NOT NULL,
    checked boolean DEFAULT false NOT NULL,
    created_at timestamp DEFAULT now() NOT NULL,
    updated_at timestamp DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS focus_sessions (
    id serial PRIMARY KEY NOT NULL,
    user_id text NOT NULL,
    officer_id varchar(32) DEFAULT 'yuri' NOT NULL,
    duration_minutes integer DEFAULT 25 NOT NULL,
    completed_at timestamp DEFAULT now() NOT NULL,
    coins_earned integer DEFAULT 15 NOT NULL,
    distraction_count integer DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS user_stats (
    id serial PRIMARY KEY NOT NULL,
    user_id text NOT NULL,
    total_coins integer DEFAULT 0 NOT NULL,
    consecutive_days integer DEFAULT 0 NOT NULL,
    total_sessions integer DEFAULT 0 NOT NULL,
    last_session_date timestamp,
    unlocked_badges text[] DEFAULT '{}' NOT NULL,
    updated_at timestamp DEFAULT now() NOT NULL,
    CONSTRAINT user_stats_user_id_unique UNIQUE (user_id)
);

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS deadline timestamp;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS scheduled_start_at timestamp;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS scheduled_end_at timestamp;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS scheduled_focus_segments jsonb;

ALTER TABLE focus_sessions ADD COLUMN IF NOT EXISTS task_id integer;
ALTER TABLE focus_sessions ADD COLUMN IF NOT EXISTS outcome varchar(32) DEFAULT 'completed' NOT NULL;

CREATE INDEX IF NOT EXISTS auth_sessions_user_id_idx ON auth_sessions USING btree (user_id);
CREATE INDEX IF NOT EXISTS auth_sessions_expires_at_idx ON auth_sessions USING btree (expires_at);
CREATE INDEX IF NOT EXISTS users_email_idx ON users USING btree (email);
CREATE INDEX IF NOT EXISTS users_created_at_idx ON users USING btree (created_at);
