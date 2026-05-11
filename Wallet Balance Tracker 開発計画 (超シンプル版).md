# Wallet Balance Tracker 開発計画 (超シンプル版)

「超軽量・超高速・超シンプル」を最優先とした、個人用ユーティリティとしての開発計画です。
技術的な美しさよりも、起動速度、入力速度、メンテナンスのしやすさを重視します。

## 1. プロジェクト概要
- **アプリ名**: Wallet Balance Tracker
- **パッケージ名**: `jp.maki.wallettracker`
- **Androidバージョン**: 最小SDK 24
- **主要技術**: Kotlin, Jetpack Compose, Room (SQLite)
- **設計方針**: 最小限のファイル構成、Repository層の廃止、Navigationの不使用

## 2. 開発フェーズ

### Phase 1: 最小機能の実装 (最優先)
1. **プロジェクト初期化**
   - 最小限の依存関係 (Compose, Room) のみを追加。
2. **データ層の一体化実装**
   - `Transaction` (Entity), `TransactionDao`, `AppDatabase` を最小限のファイル数で作成。
3. **UIの一体化実装**
   - **MainActivity**: ViewModelと全UI (メイン表示 + 入力用BottomSheet) を集約。
   - 画面遷移を排除し、状態管理による表示切り替えを採用。

### Phase 2: 機能強化 & UX
1. **差額確認機能**: Dialogによるシンプルな実装。
2. **UX改善**: 触覚フィードバック、数値の視認性向上。

---

## 3. 技術的詳細 (シンプル構成)

### [Component] データ & ロジック
#### [NEW] Database.kt
- Entity, DAO, Database クラスをここに集約。
- ViewModelから直接DAOを操作し、Repository層を排除。

### [Component] UI
#### [NEW] MainActivity.kt
- `ViewModel` をこのファイル内に定義。
- メイン画面（残高・履歴）を Composable として定義。
- 入力機能は `ModalBottomSheet` を使用し、Navigationライブラリを不使用。

---

## 4. 検証プラン
- **実機検証**: 起動が瞬時であること、入力から保存までがストレスなく行えることを確認。
- **データ永続性**: アプリ終了後も残高が正しく保持されることを確認。
