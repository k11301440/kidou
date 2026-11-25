# KIDOU（試作プロトタイプ v0.4.0）

未知の言語を自分の辞書で解読しながら、失われた遺跡の未知装置を段階的に起動していくAndroidゲーム（Kotlin / Jetpack Compose）。

## v0.4.0 の主な実装内容

- **ボード3・4 全25問実装（全100レベル＋ボード5解禁枠）**:
  - ボード1〜4を各25問（計100レベル）実装し、各ボード20/25起動で次のボード（ボード4の20/25でボード5解禁確認用スロット枠）が開放される。
  - ボード1〜4それぞれの記録（ストーリー）を収録し、ストーリー見出しのボード番号を五進数グリフ表記へ変更。
- **新ギミック追加（すべて制限時間なし・失敗ペナルティなし）**:
  - **4ボタン順番押し（Order Pad）**: 「最初 / 次 / 最後」などの語彙手がかりを読み解いて正しい順に押す。
  - **周期信号（五進数表示）**: 一定間隔で切り替わる五進数デジタル表示に合わせて起動する（タイミングを外しても失敗にならず、何度でも試行可能）。
  - **記憶モード**: 初回操作後に指示プレートを伏せ、必要時は「再表示」ボタンでいつでも再確認できる。
  - **連動スイッチ・3方向ルート・数値合算・複合条件**: スイッチ間の相互反転、3択分岐ルート、ダイヤル/バルブ一致・合計条件など多彩な論理パズルを追加。
- **UI・操作感・フォント刷新**:
  - アプリ全体のフォントに `DotGothic16` を採用し、ピクセル調の視認性と世界観を統一。
  - ボタン・操作パッドに物理スイッチ風の押し込みアニメーション（2dp沈み・ベベル反転・配色反転）と、設定でON/OFF可能なハプティクス（振動）フィードバックを追加。
  - ボード画面右上の「装置一覧ボード」表示削除、ストーリー下部固定説明文削除、カラーテーマ名簡素化（デフォルト / グリーン / ブルー / レッド）。
- **辞書・広告・音声（継続機能）**:
  - 単語登録・未登録語タップ・日英切り替え・ダーク／ライトモード・リワード広告テスト対応・各種検証用プリセット（ボード1〜4の19/25起動済みセット）。
  - BGM: "Loading Screen" by Purrple Cat（CC BY 4.0 / https://opengameart.org/content/loading-screen ）
  - フォント: DotGothic16（SIL Open Font License 1.1 / https://github.com/fontworks-fonts/DotGothic16 ）

## データ定義ファイル

- `app/src/main/assets/glyphs.json`: 12×12ピクセルグリフ定義（66種）
- `app/src/main/assets/words.json`: 日英対応の語彙テーブル（66語、初期セット7語含む）
- `app/src/main/assets/boards.json`: サブタイトル、説明文、ボード1〜4（各25レベル）＋ボード5（解禁確認用25スロット枠）定義

## ビルド・テスト

```powershell
# 単体テスト実行
.\gradlew.bat testDebugUnitTest

# デバッグAPKビルド
.\gradlew.bat assembleDebug

# Play Console提出用リリースAABビルド（リポジトリ外の keystore.properties を参照）
.\gradlew.bat bundleRelease
```

生成先:
- デバッグAPK: `app/build/outputs/apk/debug/app-debug.apk`
- リリースAAB: `app/build/outputs/bundle/release/app-release.aab`
