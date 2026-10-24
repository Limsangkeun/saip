# 백업에서 복원하기

백업: 매일 04:00 KST `deploy/backup.sh`가 `s3://<BACKUP_BUCKET>/daily/saip-YYYYMMDD.tar.gz`를 올린다(`db.dump` = `pg_dump -Fc`, `images/` = 업로드 이미지). 14일 뒤 수명 주기 규칙이 지운다.

서버 키(`saip-backup`)는 올리기만 할 수 있으므로 **내려받기는 AWS 콘솔**(관리자)에서 한다: S3 → 버킷 → `daily/` → 파일 체크 → 다운로드.

> 브라우저가 `.tar`로 이름을 바꿔 저장할 수 있다. 내용은 그대로 gzip이라 `tar xzf`로 풀린다.

## 로컬에서 확인 (복원 리허설, 2026-10-24에 실행)

로컬 개발 DB를 백업 내용으로 **덮어쓴다.** 앱(`bootRun`)은 먼저 멈춘다.

```bash
mkdir restore && tar xzf saip-YYYYMMDD.tar.gz -C restore
docker cp restore/db.dump saip-postgres-1:/tmp/db.dump
docker exec saip-postgres-1 pg_restore -U blog -d blog --clean --if-exists --no-owner /tmp/db.dump
cp -r restore/images/. data/images/
```

`.\gradlew bootRun` → http://localhost:8080 의 노트와 이미지가 운영과 같으면 성공.

## 서버가 통째로 사라졌을 때

1. 새 Lightsail 인스턴스, 고정 IP 다시 연결(IP가 바뀌면 Cloudflare A 레코드 수정) → [`server-setup.md`](server-setup.md) 1~4
2. `~/saip/.env`를 `.env.example` 키대로 다시 만든다(비밀값은 비밀번호 관리자 등에서. DB 비밀번호는 새로 만들어도 된다)
3. `aws configure --profile saip-backup` (IAM에서 새 액세스 키를 만들고 옛 키는 지운다)
4. 배포 전용 공개키를 `~/.ssh/authorized_keys`에 추가하고, GitHub Secret `SSH_KNOWN_HOSTS`를 새 서버 지문으로 갱신(`ssh-keyscan -t ed25519,ecdsa,rsa <도메인>`)
5. 앱 없이 DB만 띄우고 복원한다. 백업 파일은 PC에서 `scp`로 서버에 올린다.

```bash
cd ~/saip && docker compose up -d db
mkdir restore && tar xzf saip-YYYYMMDD.tar.gz -C restore
docker compose exec -T db pg_restore -U blog -d blog --clean --if-exists --no-owner < restore/db.dump
docker compose up -d app caddy
docker compose cp restore/images/. app:/data/images/
rm -rf restore saip-YYYYMMDD.tar.gz
```

6. GitHub Actions에서 `deploy` 워크플로를 수동 실행(Run workflow)해 최신 버전으로 맞추고, 사이트·로그인·이미지를 확인한다.
