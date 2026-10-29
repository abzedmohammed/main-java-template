-- Initial schema for the authentication-first template (OTP auth, token-in-body sessions).
-- Column names use the per-entity prefix convention (usr/otp/prt/lrl/slg/eml/mtp);
-- shared audit columns (created_at/updated_at) stay unprefixed.
-- Column types mirror Hibernate's PostgreSQL output so `ddl-auto: validate` passes.

create table users (
    usr_id uuid not null,
    usr_full_name varchar(255) not null,
    usr_email varchar(255) not null unique,
    usr_mobile_number varchar(255),
    usr_password_hash varchar(255),
    usr_role varchar(255) not null check (usr_role in ('ADMIN', 'USER')),
    usr_provider varchar(255) not null check (usr_provider in ('LOCAL', 'GOOGLE', 'GITHUB')),
    usr_provider_id varchar(255),
    usr_email_verified boolean not null,
    usr_status varchar(255) not null check (usr_status in ('DRAFT', 'ACTIVE', 'INACTIVE', 'DEACTIVATED')),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (usr_id)
);

-- Pre-auth 4-digit codes. Only an HMAC-SHA256 hex hash of the code is stored.
create table otp_tokens (
    otp_id uuid not null,
    otp_user_id uuid not null,
    otp_code_hash varchar(64) not null,
    otp_purpose varchar(255) not null check (otp_purpose in ('LOGIN', 'REGISTRATION', 'PASSWORD_RESET')),
    otp_expiry_date timestamp(6) with time zone not null,
    otp_consumed boolean not null,
    otp_attempts integer not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (otp_id)
);

-- One-off authorization for update_password, created after a verified reset OTP.
create table password_reset_tokens (
    prt_id uuid not null,
    prt_token varchar(150) not null unique,
    prt_user_id uuid not null,
    prt_expiry_date timestamp(6) with time zone not null,
    prt_used boolean not null,
    primary key (prt_id)
);

create table login_rate_limits (
    lrl_id uuid not null,
    lrl_email varchar(255) not null unique,
    lrl_attempts integer not null,
    lrl_blocked_until timestamp(6) with time zone,
    lrl_updated_at timestamp(6) with time zone not null,
    primary key (lrl_id)
);

create table system_logs (
    slg_id uuid not null,
    slg_level varchar(255) not null check (slg_level in ('INFO', 'WARN', 'ERROR', 'SECURITY')),
    slg_category varchar(255) not null,
    slg_title varchar(300) not null,
    slg_details varchar(1200) not null,
    slg_actor varchar(150),
    slg_status varchar(50),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (slg_id)
);

-- Reusable messaging content + email design layouts, both DB-driven so copy and
-- design can change at runtime without a redeploy (defaults seeded by TemplateSeeder).
--   email_templates   -> HTML design shells (OTP, GENERAL) with {{placeholders}}
--   message_templates -> per-channel content (EMAIL/SMS/IN_APP), interpolated at send time,
--                        referencing an email layout by code for the EMAIL channel.
create table email_templates (
    eml_id uuid not null,
    eml_code varchar(100) not null unique,
    eml_name varchar(255) not null,
    eml_html text not null,
    eml_active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (eml_id)
);

create table message_templates (
    mtp_id uuid not null,
    mtp_code varchar(100) not null unique,
    mtp_channel varchar(255) not null check (mtp_channel in ('EMAIL', 'SMS', 'IN_APP')),
    mtp_subject varchar(255),
    mtp_title varchar(255),
    mtp_body text not null,
    mtp_layout_code varchar(100),
    mtp_active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (mtp_id)
);

alter table otp_tokens
    add constraint fk_otp_tokens_user foreign key (otp_user_id) references users;
alter table password_reset_tokens
    add constraint fk_password_reset_tokens_user foreign key (prt_user_id) references users;

create index idx_otp_tokens_user on otp_tokens (otp_user_id);
create index idx_password_reset_tokens_user on password_reset_tokens (prt_user_id);
create index idx_system_logs_created_at on system_logs (created_at);
create index idx_system_logs_category on system_logs (slg_category);
