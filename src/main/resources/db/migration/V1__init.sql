create table category (
    id         bigserial primary key,
    slug       text not null unique,
    name       text not null,
    created_at timestamptz not null
);

create table note (
    id          bigserial primary key,
    category_id bigint not null references category (id),
    slug        text not null unique,
    title       text not null,
    body_md     text not null,
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);

create table note_revision (
    id         bigserial primary key,
    note_id    bigint not null references note (id),
    title      text not null,
    body_md    text not null,
    created_at timestamptz not null
);

create index note_revision_note_id_idx on note_revision (note_id);
