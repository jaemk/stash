create table app_users (
    id bigserial primary key,
    name text not null,
    created timestamp with time zone not null default now()
);

create table auth_tokens (
    id bigserial primary key,
    app_user bigint not null unique references "app_users" ("id") on delete cascade,
    token uuid unique not null,
    created timestamp with time zone not null default now()
);

create table items (
    id bigserial primary key,
    size bigint not null,
    path text not null,
    stash_token uuid unique not null,
    supplied_token text not null,
    content_hash text,
    creator bigint references "app_users" ("id") on delete set null,
    created timestamp with time zone not null default now(),
    expires_at timestamp with time zone
);

create unique index token_pair on items (stash_token, supplied_token);
create index user_items on items (creator);
