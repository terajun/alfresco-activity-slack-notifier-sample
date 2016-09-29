# alfresco-activity-slack-notifier-sample
[第35回Alfresco勉強会](http://alfresco-study-group.connpass.com/event/36815/)で紹介したアクティビティフィードをSlackに送るためのサンプルコードです。  
勉強会の資料は[こちら](http://alfresco-study-group.connpass.com/event/36815/presentation/)

## 使い方
### 準備
SlackでIncoming WebHookの設定をしてWebHook URLを取得する。

### alfresco-global.propertiesの編集
以下のファイルを編集し、activities.feedNotifier.slackWebhookUrlに上で取得したWebHook URLを指定する。

src/main/amp/config/alfresco/module/alfresco-activity-slack-notifier/alfresco-global.properties

### ビルドしてAMPをAlfrescoに適用
Mavenコマンドでビルドする。
    $ mvn clean package
targetディレクトリにAMPファイルができるのでそれをAlfrescoのインストールディレクトリのampsにコピーし、AMPを適用するスクリプトを実行する。
    $ cp target/alfresco-activity-slack-notifier-1.0-SNAPSHOT.amp <alfresco_install_dir>/amps/
    $ <alfresco_install_dir>/bin/apply_amps.sh

### Alfrescoを起動
Alfrescoを起動し、ブラウザからアクティビティが記録されるような操作を実行すると10秒に1回の頻度でアクティビティフィードをSlackに送信するジョブが実行される。

### 注意点
Alfrescoのユーザ名とSlackのユーザ名が一致している前提のコードになっているため、両者が異なる場合はコードを修正する必要があります。
