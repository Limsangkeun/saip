#!/usr/bin/env bash
# 매일 DB와 업로드 이미지를 묶어 S3 daily/ 에 올린다. cron: 0 4 * * * bash ~/saip/backup.sh >> ~/saip/backup.log 2>&1
# S3 키(프로필 saip-backup)는 daily/ 에 올리기만 할 수 있다. 오래된 파일은 버킷 수명 주기 규칙이 14일 뒤 지운다.
set -euo pipefail

cd ~/saip
BUCKET=$(sed -n 's/^BACKUP_BUCKET=//p' .env)
[ -n "$BUCKET" ] || { echo "$(date '+%F %T') backup FAILED: .env에 BACKUP_BUCKET이 없다" >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
name=saip-$(date +%Y%m%d).tar.gz

docker compose exec -T db pg_dump -U blog -Fc blog > "$work/db.dump"
docker compose cp app:/data/images "$work/images" > /dev/null
tar czf "$work/$name" -C "$work" db.dump images
aws s3 cp "$work/$name" "s3://$BUCKET/daily/$name" --profile saip-backup --only-show-errors

echo "$(date '+%F %T') backup ok $name $(du -h "$work/$name" | cut -f1)"
