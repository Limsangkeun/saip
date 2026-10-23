-- 노트를 지우면 그 노트의 버전도 함께 지운다.
alter table note_revision drop constraint note_revision_note_id_fkey;
alter table note_revision
    add constraint note_revision_note_id_fkey foreign key (note_id) references note (id) on delete cascade;
