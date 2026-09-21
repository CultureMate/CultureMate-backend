# CultureMate backend

서울시 문화행사 API. Spring Boot **3.3.4** · Java **17**.

프론트 레포와 같이 씁니다. 이 서버를 **8080**에 먼저 띄운 뒤 프론트를 켭니다.

## 필요한 것

- JDK 17 (`java -version`)
- Maven은 이 레포의 `mvnw` / `mvnw.cmd`로 설치됩니다. 별도 Maven 설치는 없어도 됩니다.
- [서울 열린데이터광장](https://data.seoul.go.kr) 일반 인증키 (`culturalEventInfo`)
- (카카오 로그인만) [카카오 디벨로퍼스](https://developers.kakao.com) REST API 키  
  Redirect URI: `http://localhost:8080/api/auth/kakao/callback`

키는 각자 발급합니다. 다른 사람 `.env`를 공유하지 마세요.

## 실행

```bash
copy .env.example .env
```

macOS/Linux는 `cp .env.example .env` 입니다. `.env`에 `SEOUL_API_KEY`를 넣습니다.

```bash
.\mvnw.cmd spring-boot:run
```

macOS/Linux는 `./mvnw spring-boot:run` 입니다.

IntelliJ는 **클론한 이 폴더를 프로젝트로 엽니다.** Run Configuration의 Working directory가 프로젝트 루트(`.env`가 있는 곳)여야 키가 읽힙니다.

확인:

- 헬스: `GET http://localhost:8080/api/health`
- 목록: `GET http://localhost:8080/api/events?page=0&size=5`

키가 비면 목록은 `502`, 카카오 시작은 `503`입니다. 백엔드가 떠 있어도 화면 목록은 비어 보입니다.

키는 `.env`에만 둡니다. `.env` · `data/` · `target/` 은 Git에 올리지 않습니다.

AI 소개문(`POST /api/events/{eventId}/summary`)은 아직 `501`입니다. 고장이 아닙니다.

API 계약은 [docs/api-contract.md](./docs/api-contract.md) 한 파일만 둡니다. ERD는 ERDCloud가 원본입니다.
