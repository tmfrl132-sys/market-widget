# 경제지표 위젯

안드로이드 태블릿 홈 화면에 한·미 시세 10개와 다가오는 경제지표 발표 일정을 보여주는 위젯.

- 설계: `docs/superpowers/specs/2026-10-30-market-widget-design.md`
- 계획: `docs/superpowers/plans/2026-10-30-market-widget.md`
- 데이터: https://tmfrl132-sys.github.io/market-widget/data.json (30분마다 갱신)

## 태블릿에 설치하기

1. 태블릿 브라우저로 [Releases](https://github.com/tmfrl132-sys/market-widget/releases/latest)를 열고 `market-widget-v….apk`를 받는다.
2. 받은 파일을 연다. "출처를 알 수 없는 앱" 허용 화면이 나오면 브라우저(예: Chrome)에 허용을 켠다.
3. 설치한 뒤 홈 화면 빈 곳을 길게 누르고 **위젯 → 경제지표**를 끌어다 놓는다.
4. 위젯을 누르면 바로 새로고침된다. 크기를 세로로 줄이면 시세만 보인다.

새 버전도 같은 방법으로 받아 설치하면 덮어쓴다. 서명 키가 같아서 지우지 않아도 된다.

## 관리

- **값이 오래됐을 때**: 위젯 아래 갱신 시각에 날짜가 붙어 있으면 수집이 멈춘 것이다. 공개 저장소는 60일 동안 활동이 없으면 GitHub가 예약 실행을 끈다. 저장소 **Actions → collect → Enable workflow**로 다시 켠다.
- **수동 수집**: `gh api -X POST repos/tmfrl132-sys/market-widget/actions/workflows/collect.yml/dispatches -f ref=main -f "inputs[force]=true"`
- **발표 일정표**: `collector/schedule.json`. FOMC(연준 공식 일정)와 한국은행 금통위 날짜를 매년 갱신한다. 2027년 금통위 일정은 2026-10-30 기준 미발표.
- **새 APK 배포**: `git tag v0.1.1 && git push origin v0.1.1`
- **서명 키 백업**: `C:\Users\tmfrl\market-widget-keystore\` (저장소 밖). 잃어버리면 다음 업데이트 때 앱을 지우고 다시 깔아야 한다.

## 개발

```bash
uv run pytest
```

안드로이드 테스트와 빌드는 GitHub Actions(`android.yml`)에서만 돈다.
