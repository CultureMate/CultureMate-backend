# 🎫 CultureMate · Spring Backend

서울시 문화행사를 날짜·분야·지역으로 찾고, 관심 행사로 모아 두는 Spring Boot API입니다. 화면은 [CultureMate-frontend](https://github.com/CultureMate/CultureMate-frontend)가 담당합니다.

> 🔒 비밀값은 코드나 Git에 넣지 않습니다. `.env.example`을 복사해 `.env`를 만들고 각 값을 채우세요.

<br>

## 📌 현재 범위

- ✅ **구현됨:** 서울시 문화행사 목록·상세·필터, 관심 행사(찜), 카카오 로그인·세션, 마이페이지 수정·탈퇴(카카오 연결 끊기 포함), 댓글, 조회수, 홈 HOT/근처, AI 소개문, Google Places 장소 추천, 코스 저장·공유, 로컬 H2, Docker 전체 실행(프론트·백엔드·MariaDB)
- 📋 **이슈로 남김:** 상세 보완

<br>

## 📚 문서 안내

문서마다 한 가지 기준만 둡니다.

- 🔗 [API 계약](./docs/api-contract.md): URL, 요청·응답, 오류 코드
- 🗂️ ERD: ERDCloud가 원본. 로컬은 H2 + JPA `ddl-auto: update`

<br>

## ⚙️ 동작 원칙

- 행사 원본은 DB에 저장하지 않습니다. 서울시 API를 호출하고 30분 캐시합니다.
- 찜(관심 행사)은 로그인 회원 기준입니다. 세션 쿠키가 없으면 `401`입니다.
- 카카오 로그인은 `CULTUREMATE_SESSION` HttpOnly 쿠키(7일)입니다. `/me`·로그아웃은 `credentials: include`가 필요합니다.
- API 키는 `.env`에서 읽습니다. 서울시(`SEOUL_API_KEY`), 카카오(`KAKAO_REST_KEY`·`KAKAO_CLIENT_SECRET`·`KAKAO_ADMIN_KEY`), OpenAI(`OPENAI_API_KEY`), Google Places(`GOOGLE_PLACES_API_KEY`)입니다. 키가 없어도 서버는 뜨고, 그 키를 쓰는 기능만 동작하지 않습니다(`.env.example` 참고).

<br>

## 🚀 실행

JDK **17**. Maven은 `mvnw`가 받습니다.

```bash
copy .env.example .env
```

macOS/Linux는 `cp .env.example .env` 입니다. `.env`에 `SEOUL_API_KEY`를 넣습니다. 카카오를 쓰려면 `KAKAO_REST_KEY`와 콘솔 Redirect URI `http://localhost:8080/api/auth/kakao/callback`도 맞춥니다. 탈퇴 시 카카오 연결까지 끊으려면 콘솔 "앱 키 > 어드민 키"를 `KAKAO_ADMIN_KEY`에 넣습니다(서버 `.env`에만 보관).

```bash
.\mvnw.cmd spring-boot:run
```

macOS/Linux는 `./mvnw spring-boot:run` 입니다. 터미널만 있어도 됩니다. IDE를 쓰면 이 폴더를 프로젝트로 열고, Working directory가 `.env`가 있는 루트여야 합니다.

- 🖥️ API: http://localhost:8080
- ❤️ 헬스: http://localhost:8080/api/health
- 📅 목록: http://localhost:8080/api/events?page=0&size=5
- 📱 화면: http://localhost:3000 (`localhost`로 엽니다. `127.0.0.1`이면 카카오 쿠키가 어긋날 수 있습니다)

키가 비면 목록은 `502`, 카카오 시작은 `503`입니다. `.env` · `data/` · `target/` 은 Git에 올리지 않습니다.

### 🐳 전체 Docker 실행 (프론트·백엔드·MariaDB)

JDK·Node 없이 Docker Desktop만으로 전체 서비스를 띄웁니다. 두 저장소를 같은 폴더에 받습니다.

```text
CultureMate-backend/    ← 여기서 docker compose 실행
CultureMate-frontend/
```

폴더 이름이나 위치가 다르면 `.env`에 `FRONTEND_DIR`(이 폴더 기준 상대 경로)을 넣습니다. `.env`에는 `DB_PASSWORD`가 꼭 있어야 하고, 지도를 보려면 `KAKAO_MAP_KEY`(카카오맵 JavaScript 키)도 넣습니다.

```bash
docker compose up -d --build
```

- 📱 화면: http://localhost (API는 같은 주소의 `/api`로 Nginx가 백엔드에 넘깁니다)
- ❤️ 헬스: http://localhost/api/health
- 로그 보기: `docker compose logs -f backend`
- 끄기: `docker compose down` (데이터 유지) · DB까지 초기화: `docker compose down -v`

처음 빌드는 의존성 다운로드와 양쪽 테스트 때문에 몇 분 걸립니다. 테스트가 실패하면 이미지가 만들어지지 않습니다. `KAKAO_MAP_KEY` 등 프론트 값은 빌드할 때 화면 파일에 들어가므로, 바꾼 뒤에는 `--build`로 다시 빌드합니다. 그 밖의 키는 이미지에 넣지 않고 실행할 때 `.env`에서 전달합니다.

카카오 로그인·지도는 화면 주소가 `http://localhost`로 바뀌므로 카카오 디벨로퍼스 콘솔에 아래를 **추가로** 등록합니다(기존 로컬 개발용 값은 그대로 둡니다).

- 로그인 Redirect URI: `http://localhost/api/auth/kakao/callback`
- 플랫폼 Web 사이트 도메인: `http://localhost`

`.env`의 `FRONTEND_URL`·`FRONTEND_ORIGIN`·`KAKAO_REDIRECT_URI`는 로컬 개발용이라 Docker 실행에서는 쓰지 않습니다. Docker 주소를 바꿀 때만 `DOCKER_FRONTEND_URL`·`DOCKER_KAKAO_REDIRECT_URI`를 넣습니다. 80·8080 포트가 이미 쓰이면 `FRONTEND_PORT`·`BACKEND_PORT`로 바꾸고, 화면 포트를 바꿨다면 `DOCKER_FRONTEND_URL`과 카카오 콘솔 주소도 같은 포트로 맞춥니다. `PLACES_*` 호출 제한은 Docker에서 기본값으로 동작합니다.

### 🐬 MariaDB로 실행 (docker 프로필)

`mvnw`로 백엔드만 실행하면 H2를 씁니다. 백엔드는 로컬에서 돌리면서 DB만 Docker MariaDB로 쓰려면 `docker` 프로필을 켭니다.

```bash
docker compose up -d db
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

`.env`에 `DB_PASSWORD`가 있어야 합니다. 테이블은 Flyway(`src/main/resources/db/migration`)가 만들고, Hibernate는 `validate`로 검사만 합니다.
엔티티를 바꾸면 기존 SQL은 고치지 말고 `V2__설명.sql`처럼 다음 버전 파일을 함께 추가합니다. 로컬 H2는 그대로 돌아가도 MariaDB에서는 서버가 뜨지 않습니다.

- DB 도구 접속(HeidiSQL 등): `MariaDB or MySQL (TCP/IP)` / `127.0.0.1:3308` / DB `culturemate` / 사용자 `culturemate` / 암호 `DB_PASSWORD`
- PC에 설치된 MariaDB·MySQL이 3306을 쓰는 경우가 많아 기본 포트를 `3308`로 뒀습니다. 바꾸려면 `.env`의 `DB_PORT`를 고칩니다.
- 끄기: `docker compose down` (데이터 유지) · 초기화: `docker compose down -v`

<br>

## 🧪 테스트

```bash
.\mvnw.cmd test
```
