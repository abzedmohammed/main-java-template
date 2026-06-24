-- Initial schema for the authentication-first template.
-- Column types mirror Hibernate's PostgreSQL output so `ddl-auto: validate` passes.

create table users (
    id uuid not null,
    full_name varchar(255) not null,
    email varchar(255) not null unique,
    password_hash varchar(255),
    role varchar(255) not null check (role in ('ADMIN', 'USER')),
    provider varchar(255) not null check (provider in ('LOCAL', 'GOOGLE', 'GITHUB')),
    provider_id varchar(255),
    email_verified boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (id)
);

create table refresh_tokens (
    id uuid not null,
    token varchar(500) not null unique,
    user_id uuid not null,
    expiry_date timestamp(6) with time zone not null,
    revoked boolean not null,
    primary key (id)
);

create table password_reset_tokens (
    id uuid not null,
    token varchar(150) not null unique,
    user_id uuid not null,
    expiry_date timestamp(6) with time zone not null,
    used boolean not null,
    primary key (id)
);

create table email_verification_tokens (
    id uuid not null,
    token varchar(150) not null unique,
    user_id uuid not null,
    expiry_date timestamp(6) with time zone not null,
    used boolean not null,
    primary key (id)
);

create table login_rate_limits (
    id uuid not null,
    email varchar(255) not null unique,
    attempts integer not null,
    blocked_until timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    primary key (id)
);

create table system_logs (
    id uuid not null,
    level varchar(255) not null check (level in ('INFO', 'WARN', 'ERROR', 'SECURITY')),
    category varchar(255) not null,
    title varchar(300) not null,
    details varchar(1200) not null,
    actor varchar(150),
    status varchar(50),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    primary key (id)
);

alter table refresh_tokens
    add constraint fk_refresh_tokens_user foreign key (user_id) references users;
alter table password_reset_tokens
    add constraint fk_password_reset_tokens_user foreign key (user_id) references users;
alter table email_verification_tokens
    add constraint fk_email_verification_tokens_user foreign key (user_id) references users;

create index idx_refresh_tokens_user on refresh_tokens (user_id);
create index idx_password_reset_tokens_user on password_reset_tokens (user_id);
create index idx_email_verification_tokens_user on email_verification_tokens (user_id);
create index idx_system_logs_created_at on system_logs (created_at);
create index idx_system_logs_category on system_logs (category);
