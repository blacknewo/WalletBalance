# Android リリース署名 完全設定ガイド

## 全体の流れ

```
Step 1: キーストア（.jks）ファイルを作成する
Step 2: gradle.properties に署名情報を安全に記載する
Step 3: app/build.gradle に signingConfig を設定する
Step 4: .gitignore でキーストアと署名情報を除外する
Step 5: リリースビルドを生成する
```

> [!CAUTION]
> キーストア（.jks）ファイルは**絶対に紛失・流出させてはいけません**。
> 失うと同じアプリとして永久にアップデートできなくなります。
> **必ず安全な場所にバックアップ**してください（USBメモリ、クラウドストレージなど）。

---

## Step 1: キーストアファイルを作成する

Android Studio のメニューから作成します。

1. メニューバー →「**Build**」→「**Generate Signed App Bundle / APK...**」
2. 「**APK**」を選択 → 「Next」
3. 「**Create new...**」ボタンをクリック

### 入力項目の詳細

| 項目 | 説明 | 例 |
|---|---|---|
| **Key store path** | .jks ファイルの保存場所 | `C:\Users\newo2\Documents\maki-release-key.jks` |
| **Password** (keystore) | キーストア全体のパスワード | 任意の強力なパスワード |
| **Alias** | キーの名前（識別子） | `maki-key` |
| **Password** (key) | このキー個別のパスワード | 任意（keystoreと同じでも可） |
| **Validity (years)** | 有効期限 | `25`（25年推奨） |
| **First and Last Name** | 氏名（証明書の情報） | 任意（公開されません） |
| **Country Code** | 国コード | `JP` |

> [!IMPORTANT]
> **Key store path は プロジェクトフォルダ外** に保存することを強く推奨します。
> プロジェクトフォルダ内に置くと、誤って Git にコミットするリスクがあります。
> 例: `C:\Users\newo2\Documents\maki-release-key.jks`

入力後「OK」でファイルが生成されます。

---

## Step 2: gradle.properties に署名情報を記載する

キーストアのパスとパスワードを **ソースコードに直書きせず** `gradle.properties` に記載します。
`gradle.properties` は `.gitignore` で除外するため、Git に漏れません。

[gradle.properties](file:///c:/Users/newo2/Documents/WalletBalance/gradle.properties) の末尾に以下を追加します：

```properties
# --- リリース署名情報（Git には絶対コミットしないこと）---
RELEASE_STORE_FILE=C:/Users/newo2/Documents/maki-release-key.jks
RELEASE_STORE_PASSWORD=ここにキーストアのパスワード
RELEASE_KEY_ALIAS=maki-key
RELEASE_KEY_PASSWORD=ここにキーのパスワード
```

> [!WARNING]
> パスの区切り文字は Windows でも **`/`（スラッシュ）** を使用してください。
> `\`（バックスラッシュ）はエスケープが必要になります。

---

## Step 3: app/build.gradle に signingConfig を設定する

[app/build.gradle](file:///c:/Users/newo2/Documents/WalletBalance/app/build.gradle) を以下のように変更します。

### 変更前

```groovy
android {
    // ...

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.debug   // ← デバッグ証明書（要変更）
        }
    }
}
```

### 変更後

```groovy
android {
    // ...

    signingConfigs {
        release {
            storeFile     file(RELEASE_STORE_FILE)
            storePassword RELEASE_STORE_PASSWORD
            keyAlias      RELEASE_KEY_ALIAS
            keyPassword   RELEASE_KEY_PASSWORD
        }
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release  // ← リリース証明書に変更
        }
    }
}
```

---

## Step 4: .gitignore でキーストアと署名情報を保護する

プロジェクトルートの `.gitignore` に以下を追加・確認します。

```gitignore
# キーストアファイル（絶対にコミットしない）
*.jks
*.keystore

# 署名情報を含む gradle.properties（絶対にコミットしない）
gradle.properties
```

> [!NOTE]
> `gradle.properties` を丸ごと除外する場合、他の開発者は自分でファイルを用意する必要があります。
> チーム開発でなく個人開発であれば、これが最もシンプルで安全です。

---

## Step 5: リリース APK を生成する

1. メニューバー →「**Build**」→「**Generate Signed App Bundle / APK...**」
2. 「**APK**」を選択 → 「Next」
3. Key store path / Password / Alias / Key password を入力（Step 1 で作成したもの）
4. Build Variant：「**release**」を選択
5. 「**Finish**」でビルド開始

生成された APK の場所：
```
app\release\app-release.apk
```

---

## 設定完了後の確認

ビルドが成功したら、以下のコマンドで署名を確認できます（参考）：

```powershell
# keytool で署名情報を表示（Android Studio の Terminal から）
keytool -printcert -jarfile app\release\app-release.apk
```

---

## まとめ: 各ファイルの役割

| ファイル | 役割 | Git管理 |
|---|---|---|
| `maki-release-key.jks` | 署名の本体（最重要） | ❌ 除外 |
| `gradle.properties` | パスワードなど署名情報 | ❌ 除外 |
| `app/build.gradle` | 署名設定の参照先 | ✅ コミット可 |
| `.gitignore` | 除外ルール | ✅ コミット可 |
