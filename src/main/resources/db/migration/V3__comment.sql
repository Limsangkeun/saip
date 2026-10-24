-- 노트 댓글. 작성자는 Google sub(바뀌지 않는 계정 번호)로 식별하고 이메일은 저장하지 않는다.
create table comment (
    id          bigserial primary key,
    note_id     bigint      not null references note (id) on delete cascade,
    author_sub  text        not null,
    author_name text        not null,
    body        text        not null,
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);

create index comment_note_id_idx on comment (note_id);
