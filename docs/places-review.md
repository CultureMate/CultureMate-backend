# Places PR 리뷰 반영

## 추천 순위

`/between`은 최대 20개의 Google 후보를 받아 서버에서 다음 순서로 처리한다.

1. 평점 누락, 평가 수 5개 미만, 임시휴업/폐업 후보를 제외한다.
2. 좌표가 없거나 유효하지 않은 후보를 제외한다.
3. 우회거리 `D = max(0, 거리(행사1, 후보) + 거리(후보, 행사2) - 거리(행사1, 행사2))`를 계산한다.
4. 허용 우회거리 `L = min(2000m, max(300m, 행사 간 거리 × 0.5))`를 넘는 후보를 제외한다.
5. 남은 후보의 평균 평점 C를 구하고, 아래 점수가 높은 순서로 정렬한다.

```text
B = (v × R + 10 × C) / (v + 10)
최종 점수 = B - 0.5 × (D / L)
```

R은 원래 평점, v는 `userRatingCount`(평가 참여 수)이다. Google의 텍스트 리뷰 수를 별도로 받지 않으므로 평가 수와 리뷰 수를 두 독립 지표처럼 중복 반영하지 않는다. 평가가 적은 장소는 후보 평균 쪽으로 보정되고, 평가가 많으면 원래 평점이 더 많이 반영된다. 그다음 우회거리에 따라 0~0.5점만 감점한다. 0.5는 평점의 영향력을 유지하려는 초기 정책값이며 사용자 반응에 따라 조정할 수 있다.

예를 들어 허용 우회거리 1000m일 때 다음과 같다.

| 후보 | 베이지안 점수 B | 우회거리 | 최종 점수 |
|---|---:|---:|---:|
| A | 4.5 | 100m | 4.45 |
| B | 4.8 | 400m | 4.60 |
| C | 4.5 | 600m | 4.20 |

B → A → C 순서가 된다. 조금 더 돌아가도 품질 차이가 충분하면 먼저 추천하며, 품질이 같으면 덜 돌아가는 곳이 먼저 나온다. 점수가 같으면 우회거리, 장소 ID 순서로 정렬한다. 응답의 `detourMeters`, `recommendationScore`로 결과를 확인할 수 있다. `/nearby`는 기존 베이지안 순위를 유지하며 이 두 필드는 null이다.

거리는 하버사인 공식으로 구한 지표면상 직선거리 추정이다. 실제 도로·도보 경로의 거리나 이동 시간은 아니다. 강, 다리, 출입구 등은 반영하지 않는다. 실제 이동 경로 기준으로 바꾸려면 별도 경로 API와 비용 정책이 필요하다. 행사 좌표가 같아도 최소 허용량 300m가 적용되어 0으로 나누지 않는다.

## 호출 제한과 동시성

| 구분 | 검색(nearby + between 공유) | 사진 |
|---|---:|---:|
| 동일 IP, 고정 1분 구간 | 6회 | 12회 |
| 서버 전체, 일일 | 80회 | 60회 |
| 서버 전체, 월간 | 900회 | 900회 |

월간/분당 값은 `PLACES_SEARCH_MONTHLY_LIMIT`, `PLACES_PHOTO_MONTHLY_LIMIT`, `PLACES_SEARCH_PER_MINUTE`, `PLACES_PHOTO_PER_MINUTE` 환경변수로 변경한다. 0은 해당 호출을 차단한다. IP 한도 초과는 429 `PLACES_RATE_LIMITED`, 전체 한도 초과는 503 `PLACES_QUOTA_EXCEEDED`이다. 분 제한은 정각 분 단위 고정 구간이므로 경계 전후에는 짧은 시간에 두 구간 분량이 허용될 수 있다.

`PlacesQuotaService`가 별도 `TransactionTemplate`의 REQUIRES_NEW 트랜잭션에서 IP 제한, 월간 제한, 일일 제한을 함께 검사하고 저장한다. Google 호출은 커밋이 끝난 뒤에만 실행한다. `places_quota_bucket`의 월별 행에 `@Version`을 두어 동시 갱신을 감지하고, 충돌하면 전체 트랜잭션을 새로 시작한다(최대 30회). 첫 행 생성 충돌도 재시도한다. 한도에 걸리면 어떤 카운터도 차감되지 않는다. Google로 나간 요청은 실패하거나 응답이 유실되어도 카운트를 돌려주지 않는다. 네트워크 실패가 실제 미과금이라는 보장이 없기 때문이다.

기존 `places_api_usage`의 일별 기록을 월간 합산하므로 배포하면서 이번 달 사용량이 0으로 초기화되지 않는다. 모든 서버가 같은 DB를 사용해야 한다. 서버마다 별도 H2 파일을 사용하면 전역 제한을 공유할 수 없다. 여러 인스턴스로 배포할 때는 같은 DB 서버에 연결해야 한다. 구버전은 새 월별 잠금 규칙을 따르지 않으므로 구버전·신버전의 동시 운영을 피하고 전환한다.

일/월 경계는 `America/Los_Angeles`(미국 태평양 시간, 서머타임 반영)이다. Google 무료 사용량이 태평양 시간 월초에 갱신되기 때문이다. 기존 기록은 과거 서버의 날짜 기준으로 기록되어 있으므로 최초 전환 월에는 Console 집계와 대조해야 한다. 기존 기록이 누락되어 있거나 같은 결제 계정의 다른 프로젝트에서도 사용하면 애플리케이션 카운터만으로 무료 사용량 전체를 보장할 수 없다.

IP는 `request.getRemoteAddr()`로 읽고 원문 대신 SHA-256 해시를 저장한다. 사용자가 보낸 `X-Forwarded-For`를 직접 신뢰하지 않는다. 프록시 뒤에서 운영한다면 신뢰할 수 있는 프록시만 실제 클라이언트 주소를 전달하도록 서버/프록시를 설정해야 한다. 설정이 없으면 프록시 IP 하나로 합산된다. 같은 공용 IP를 쓰는 사용자들은 제한을 공유한다. IP별 버킷은 분마다 새로 생성하지 않고 같은 행을 재사용한다.

## 요청 검증

Google API 호출과 사용량 차감 전에 검증한다.

- 좌표: NaN/Infinity 불가. 기존 국내 범위(위도 33~39, 경도 124~132) 유지.
- radius: 1~50000. 누락하면 기존 기본값 500m.
- types: Google Places Table A 허용 목록, 최대 50개. 누락/빈 목록은 기존 기본 타입 restaurant+cafe. Table B의 food/establishment 등은 거절.
- between type: cafe 또는 restaurant.
- photo name: `places/{placeId}/photos/{photoId}`. 식별자에는 영문·숫자·밑줄·하이픈만 허용하며 쿼리 문자열, 추가 경로, URL 등은 거절.
- 필수 쿼리 파라미터 누락과 숫자 변환 실패: 400 `INVALID_PARAM`.

Table A 스냅샷은 `GooglePlaceTypes`에 있으며 2026-09-28 공식 문서를 확인했다. Google이 타입을 추가하면 이 목록도 갱신한다.

## 사진 응답 변경

기존 `photoAttribution` 문자열 대신 `authorAttributions` 배열을 반환한다. 현재 선택하는 첫 번째 사진의 모든 저작자를 순서대로 보존한다. 출처가 없으면 빈 배열이고 개별 필드가 없으면 null이다.

```json
{
  "photoName": "places/p1/photos/abc",
  "authorAttributions": [
    {"displayName": "저작자1", "uri": "https://example.com/a", "photoUri": "https://example.com/a.jpg"},
    {"displayName": "저작자2", "uri": "https://example.com/b", "photoUri": "https://example.com/b.jpg"}
  ]
}
```

프론트는 문자열 접근을 배열 순회로 바꾸고 사진 옆에 전체 출처를 표시해야 한다. 이 저장소에서는 백엔드 응답까지 변경했다.

## Google Cloud 확인 항목

프로젝트 Console에 접근한 상태가 아니므로 실제 예산/할당량 설정은 확인하거나 변경하지 않았다. 운영자가 다음을 확인해야 한다.

1. Billing의 Reports/Cost table에서 해당 결제 계정 전체의 Nearby Search Enterprise 및 Place Details Photos 사용량과 무료 한도를 확인한다. 현재 공식 요금표의 무료 한도는 두 SKU 각각 월 1000회이며, 애플리케이션 기본값은 여유분 100회를 둔 900회이다.
2. Billing → Budgets & alerts에 월 예산과 알림 수신자, 50%/80%/100% 등의 알림 임계값을 설정한다. 예산 알림은 사용을 자동 차단하지 않는다.
3. Google Maps Platform → Quotas에서 Places API의 검색/사진 관련 메서드별 제공 할당량을 확인하고 운영량에 맞춰 제한한다. 모든 SKU에 월간 하드캡이 제공된다고 가정하지 않는다. 앱 월간 제한도 유지한다.
4. API 키를 Places API 및 서버의 허용 IP로 제한하고, 브라우저나 다른 서비스에서 같은 키/예산을 소비하는지 확인한다.
5. 배포 전 기존 DB의 이달 기록과 Console의 과금 사용량을 비교한다. 다른 프로젝트 사용량이 있다면 월간 환경변수를 그만큼 낮춘다.

근거: [타입 목록](https://developers.google.com/maps/documentation/places/web-service/place-types), [공식 요금표](https://developers.google.com/maps/billing-and-pricing/pricing), [무료 사용량 갱신 시점](https://developers.google.com/maps/billing-and-pricing/overview), [비용·예산 관리](https://developers.google.com/maps/billing-and-pricing/manage-costs), [Places 할당량](https://developers.google.com/maps/documentation/places/web-service/usage-and-billing).

## 검증

`./mvnw.cmd test`로 전체 테스트를 실행한다. MockMvc로 400 응답과 외부 호출 미발생, 클라이언트로 검증 전 카운터 미차감 및 다중 출처 파싱, 실제 H2/JPA로 영속화·IP 제한·일/월 제한·동시 생성 충돌·월 경계 갱신을 검증한다. 실제 Google API 호출은 테스트에서 수행하지 않는다.
