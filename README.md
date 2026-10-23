# 모닝알람

광고 없고, **익숙해질 수 없는** 아침 알람. 수면 연구에서 사람을 가장 확실하게 깨운 신호들을 조합해 매일 실시간으로 합성하고, 일어날 때까지 끝나지 않습니다.

## 어떻게 깨우나

| 시간 | 일어나는 일 |
|---|---|
| 0초 | **520Hz 사각파 T3 패턴**(0.5초 켜짐·꺼짐 ×3, 1.5초 쉼). 볼륨 강제 최대, 6초 동안 50%→100%. 진동. 화면 최대 밝기 + 1Hz 점멸 |
| 20초 | 저/고음 교차 + **30–150Hz 거칠기(roughness)** 추가. **이름을 낯선 목소리(TTS)로 부름** (22초 간격) |
| 45초~ | 예측 불가능한 카오스 모드: 사이렌 스윕, 연타음, 불협화음 클러스터, 침묵 뒤 갑작스러운 소리 |
| 다시 알림 | iOS처럼 큰 **다시 알림** 버튼 (기본 5분 × 최대 3회, 설정 가능). 다시 울릴 땐 20초 단계부터 바로 시작. 횟수 소진 시 버튼 사라짐 |
| 끄기 | 밀어서 → 미션: **암산**(N문제) / **패턴**(미리 등록, 2–3초) / **캡챠**("빨간 동그라미 모두 선택", 한 판 3–5초). 미션 중 소리는 그대로/작게/끄기 선택. 30초 손 놓으면 처음부터 |
| 끈 뒤 3분 | **"깨어 있나요?"** 60초 안에 안 누르면 → 최고 강도로 다시 울림 |

볼륨: 울릴 때 알람 볼륨을 자동으로 최대(음소거도 해제)로 올리고, 끄거나 다시 알림을 누르면 **원래 볼륨으로 복원**. 원래 값은 디스크에 저장돼서 앱이 죽거나 폰이 재부팅돼도 복원됩니다.

탈출 방지: 뒤로가기·볼륨 키 무시, 볼륨을 줄여도 1초 안에 원상복구, 홈 버튼·전원 버튼으로 나가면 1.5초 뒤 다시 화면을 끌어옴(“다른 앱 위에 표시” 권한 필요), 알림에 끄기 버튼 없음, 프로세스가 죽어도 서비스가 재시작되며 다시 울림, 재부팅 후 잠금 해제 전에도 울림.

## 과학적 근거

1. **저주파 사각파가 고주파 삐삐보다 훨씬 잘 깨운다**
   Bruck, Ball, Thomas & Rouillard (2009). *How does the pitch and pattern of a signal affect auditory arousal thresholds?* J Sleep Res 18(2):196–203.
   Bruck & Thomas (2009). *Smoke alarms for sleeping adults who are hard-of-hearing.* Ear Hear 30(1):73–80.
   → 520Hz 사각파 T3 패턴이 3100Hz 경보음보다 낮은 음량에서 깨웠고, 미국 NFPA 72의 침실용 저주파 경보 요건 근거가 됨. 사각파는 홀수 배음이 넓게 퍼져 여러 청력 대역을 동시에 자극.
2. **거칠기(roughness)는 뇌의 경보 채널이다**
   Arnal et al. (2015). *Human screams occupy a privileged niche in the communication soundscape.* Curr Biol 25(15):2051–2056.
   Arnal et al. (2019). *The rough sound of salience enhances aversion through neural synchronisation.* Nat Commun 10:3671.
   → 30–150Hz 진폭변조가 편도체를 자극하고 회피 반응을 유발.
3. **습관화 — 10년 쓴 알람이 안 들리는 이유**
   Rankin et al. (2009). *Habituation revisited.* Neurobiol Learn Mem 92(2):135–138.
   → 반복 자극엔 반응이 줄고, 새로운 자극은 반응을 회복시킨다(탈습관화). 그래서 음원 파일이 없고, 매일 다른 시드로 음높이(±8%)·거칠기 주파수·패턴을 바꿔 합성.
4. **자기 이름 + 낯선 목소리**
   Perrin et al. (1999). *A differential brain response to the subject's own name persists during sleep.* Clin Neurophysiol 110(12):2153–2164.
   Blume et al. (2018). *Standing sentinel during human sleep.* NeuroImage 178:638–648.
5. **빛**
   Cajochen (2007). *Alerting effects of light.* Sleep Med Rev 11(6):453–464.
   Hilditch et al. (2022). *Rise and shine: … short-wavelength-enriched light to mitigate sleep inertia.* J Sleep Res 31(5):e13558.
   → 점멸은 1Hz로 광과민성 발작 위험 대역(3Hz 이상)을 피함.
6. **수면 관성 — 끄고 다시 자기 방지**
   Tassi & Muzet (2000). *Sleep inertia.* Sleep Med Rev 4(4):341–353.
   → 암산 미션 + 몇 분 뒤 기상 확인.
7. **트레이드오프**: McFarlane et al. (2020, PLOS ONE)는 멜로디 알람이 기상 후 멍함을 줄인다고 보고. 이 앱은 *확실한 각성*을 우선하고, 멍함은 빛과 인지 과제로 상쇄.

## 설치

`release/morningalarm.apk`를 폰에 옮겨 설치(출처를 알 수 없는 앱 허용). 첫 실행 후 **설정 탭 › 권한**을 전부 초록색으로 만들고, **10초 후 테스트 알람**으로 확인하세요.

삼성: 설정 › 배터리 › 백그라운드 사용 제한 › **절전 예외 앱**에 추가 권장.

## 빌드

- JDK 17, Android SDK 34
- `./gradlew assembleRelease`
- 프로젝트 경로에 한글이 있으면 단위 테스트 워커가 깨집니다. Windows에서는 `subst M: "<프로젝트 경로>"` 후 `M:`에서 실행하세요.
- `./gradlew testDebugUnitTest` → `app/build/preview_alarm.wav`에 90초 알람 미리듣기가 렌더링됩니다.

## 구조

```
alarm/   AlarmScheduler(setAlarmClock) · AlarmReceiver · BootReceiver · AlarmService(포그라운드, 볼륨/진동/TTS/플래시/화면 복귀) · AlarmState
sound/   WakeSoundEngine(PolyBLEP 사각파 실시간 합성) · VoiceCaller(TTS)
mission/ MathMission
ui/      iOS 스타일 Compose UI — 알람 목록, 휠 피커 편집 시트, 설정, 알람/미션/기상 확인 화면
```

서체: [Pretendard](https://github.com/orioncactus/pretendard) (SIL OFL 1.1), KS X 1001 한글 2,350자로 서브셋.
