create table items (
    id bigserial primary key,
    size bigint not null,
    path text not null,
    token uuid unique not null,
    content_hash text,
    created timestamp with time zone not null default now(),
    expires_at timestamp with time zone
);
