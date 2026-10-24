# 서버 준비 기록

Lightsail 인스턴스를 처음 만들 때 실행한 명령. 서버를 다시 만들 때 이 순서대로 하면 된다. 비밀값은 여기에 적지 않는다.

- 인스턴스: Lightsail 1GB(2 vCPU, 40GB SSD), Ubuntu 24.04 LTS, x86_64, 리전 시드니(`ap-southeast-2`, 무료 체험 리전)
- 고정 IP 연결, IPv4·IPv6 방화벽: 22, 80, 443만
- 접속: `ssh -i ~/.ssh/saip-lightsail.pem ubuntu@<고정 IP>`

## 1. 업데이트, 스왑, 시간대

```bash
sudo apt-get update && sudo DEBIAN_FRONTEND=noninteractive apt-get -y upgrade

# 스왑 2GB: 메모리 1GB가 순간적으로 모자라도 프로세스가 죽지 않고 느려지기만 하게
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
# 스왑은 정말 모자랄 때만 쓰게(기본 60)
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf && sudo sysctl --system

# 리전은 시드니지만 백업 시각(cron) 등은 한국 시간으로
sudo timedatectl set-timezone Asia/Seoul
```

## 2. Docker (공식 저장소)

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu   # 다시 접속하면 sudo 없이 docker
```

## 3. 보안 업데이트 자동 설치

Ubuntu 클라우드 이미지에는 `unattended-upgrades`가 기본으로 켜져 있다. 확인만 한다.

```bash
systemctl is-enabled unattended-upgrades
cat /etc/apt/apt.conf.d/20auto-upgrades   # 두 값 모두 "1"
```

첫 설정 때 커널 업데이트로 재부팅이 필요했다. 커널 업데이트 뒤 `/var/run/reboot-required`가 있으면 `sudo reboot`. 컨테이너는 `restart: unless-stopped`라 재부팅 뒤 자동으로 다시 뜬다.

## 4. 배포 파일

```bash
mkdir -p ~/saip && chmod 700 ~/saip
# PC에서: scp -i ~/.ssh/saip-lightsail.pem deploy/compose.yaml deploy/Caddyfile ubuntu@<고정 IP>:~/saip/
# ~/saip/.env 는 서버에서 만든다(chmod 600). 키 목록은 deploy/.env.example
```

## 5. 매일 백업 (S3)

AWS 콘솔에서(관리자): 서울 리전 버킷(퍼블릭 차단, 기본 암호화), 수명 주기 규칙 `daily/` 14일 후 만료, IAM 사용자 `saip-backup`(콘솔 로그인 없음)에 인라인 정책 하나만:

```json
{ "Version": "2012-10-17",
  "Statement": [{ "Effect": "Allow", "Action": "s3:PutObject", "Resource": "arn:aws:s3:::<버킷>/daily/*" }] }
```

서버에서:

```bash
sudo snap install aws-cli --classic
aws configure --profile saip-backup          # 액세스 키는 여기에만 입력한다
echo BACKUP_BUCKET=<버킷> >> ~/saip/.env     # backup.sh는 배포 때 함께 올라온다
bash ~/saip/backup.sh                         # "backup ok ..." 확인
(crontab -l 2>/dev/null; echo "0 4 * * * bash ~/saip/backup.sh >> ~/saip/backup.log 2>&1") | crontab -
```

복원은 [`restore.md`](restore.md).
