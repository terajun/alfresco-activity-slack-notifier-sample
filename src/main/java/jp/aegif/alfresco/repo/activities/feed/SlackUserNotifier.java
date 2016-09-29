package jp.aegif.alfresco.repo.activities.feed;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.activities.feed.AbstractUserNotifier;
import org.alfresco.repo.security.authentication.AuthenticationContext;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;

import com.google.gson.Gson;

public class SlackUserNotifier extends AbstractUserNotifier implements InitializingBean {
    private List<String> excludedEmailSuffixes;

    private AuthenticationContext authenticationContext;

    private String slackWebhookUrl;
    static final private String PAYLOAD_FORMAT = "payload='{'\"channel\": \"{0}\", \"username\": \"{1}\", \"text\": \"{2}: {3} by {4} on {5}\", \"icon_emoji\": \":ghost:\"'}'";

    public void setAuthenticationContext(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    public static Log getLogger() {
        return logger;
    }

    public static void setLogger(Log logger) {
        SlackUserNotifier.logger = logger;
    }

    public List<String> getExcludedEmailSuffixes() {
        return excludedEmailSuffixes;
    }

    public void setExcludedEmailSuffixes(List<String> excludedEmailSuffixes) {
        this.excludedEmailSuffixes = excludedEmailSuffixes;
    }

    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }

    /**
     * Perform basic checks to ensure that the necessary dependencies were
     * injected.
     */
    protected void checkProperties() {
        super.checkProperties();

        PropertyCheck.mandatory(this, "authenticationContext", authenticationContext);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        checkProperties();
    }

    protected boolean skipUser(NodeRef personNodeRef) {
        Map<QName, Serializable> personProps = nodeService.getProperties(personNodeRef);
        String feedUserId = (String) personProps.get(ContentModel.PROP_USERNAME);
        String emailAddress = (String) personProps.get(ContentModel.PROP_EMAIL);
        Boolean emailFeedDisabled = (Boolean) personProps.get(ContentModel.PROP_EMAIL_FEED_DISABLED);

        if ((emailFeedDisabled != null) && (emailFeedDisabled == true)) {
            return true;
        }

        if (authenticationContext.isSystemUserName(feedUserId) || authenticationContext.isGuestUserName(feedUserId)) {
            // skip "guest" or "System" user
            return true;
        }

        if ((emailAddress == null) || (emailAddress.length() <= 0)) {
            // skip user that does not have an email address
            if (logger.isDebugEnabled()) {
                logger.debug("Skip for '" + feedUserId + "' since they have no email address set");
            }
            return true;
        }

        String lowerEmailAddress = emailAddress.toLowerCase();
        for (String excludedEmailSuffix : excludedEmailSuffixes) {
            if (lowerEmailAddress.endsWith(excludedEmailSuffix.toLowerCase())) {
                // skip user whose email matches exclude suffix
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Skip for '" + feedUserId + "' since email address is excluded (" + emailAddress + ")");
                }
                return true;
            }
        }

        return false;
    }

    protected Long getFeedId(NodeRef personNodeRef) {
        Map<QName, Serializable> personProps = nodeService.getProperties(personNodeRef);

        // where did we get up to ?
        Long emailFeedDBID = (Long) personProps.get(ContentModel.PROP_EMAIL_FEED_ID);
        if (emailFeedDBID != null) {
            // increment min feed id
            emailFeedDBID++;
        } else {
            emailFeedDBID = -1L;
        }

        return emailFeedDBID;
    }

    protected void notifyUser(NodeRef personNodeRef, String subjectText, Object[] subjectParams,
            Map<String, Object> model, String templateNodeRef) {
        /*
         * EmailUserNotifierのコード ParameterCheck.mandatory("personNodeRef",
         * personNodeRef);
         * 
         * Map<QName, Serializable> personProps =
         * nodeService.getProperties(personNodeRef); String emailAddress =
         * (String)personProps.get(ContentModel.PROP_EMAIL);
         * 
         * Action mail = actionService.createAction(MailActionExecuter.NAME);
         * 
         * mail.setParameterValue(MailActionExecuter.PARAM_TO, emailAddress);
         * mail.setParameterValue(MailActionExecuter.PARAM_SUBJECT,
         * subjectText);
         * mail.setParameterValue(MailActionExecuter.PARAM_SUBJECT_PARAMS,
         * subjectParams);
         * 
         * //mail.setParameterValue(MailActionExecuter.PARAM_TEXT,
         * buildMailText(emailTemplateRef, model));
         * mail.setParameterValue(MailActionExecuter.PARAM_TEMPLATE,
         * templateNodeRef);
         * mail.setParameterValue(MailActionExecuter.PARAM_TEMPLATE_MODEL,
         * (Serializable)model);
         * 
         * actionService.executeAction(mail, null);
         */

        Gson gson = new Gson();
        try {
            JSONArray activitiesArray = new JSONArray(gson.toJson(model.get("activities")));

            String botName = "Alfresco"; // 送信元として表示される名前
            for (int i = 0; i < activitiesArray.length(); i++) {
                // アクティビティの内容からSlackに送信する要素を抽出
                JSONObject activity = activitiesArray.getJSONObject(i);
                String target = "@" + activity.getString("feedUserId"); // 宛先のユーザ名
                String activityType = activity.getString("activityType"); // アクティビティの種類
                String postUser = activity.getString("postUserId"); // アクティビティを実行したユーザ名
                String postDate = activity.getString("postDate"); // アクティビティの実行日時
                String contentName = activity.getJSONObject("activitySummary").getString("title"); // コンテンツ名

                // 抽出した要素でSlackのIncoming WebHookに送るpayloadを作成
                String payload = MessageFormat.format(PAYLOAD_FORMAT, target, botName, contentName, activityType, postUser, postDate);
                logger.debug("payload : " + payload);
//                StringBuilder payload = new StringBuilder();
//                payload.append("payload={\"channel\":\"");
//                payload.append(target); // 宛先のユーザ名
//                payload.append("\",\"username\":\"");
//                payload.append(botName); // 送信元として表示される名前
//                payload.append("\", \"text\": \"");
//                payload.append(contentName); // コンテンツ名
//                payload.append(", ");
//                payload.append(activityType); // アクティビティの種類
//                payload.append(" by ");
//                payload.append(postUser); // アクティビティを実行したユーザ名
//                payload.append(" on ");
//                payload.append(postDate); // アクティビティの実行日時
//                payload.append("\", \"icon_emoji\": \":ghost:\"}");

                // SlackのIncoming WebHookにPOST
                HttpClient httpClient = HttpClientBuilder.create().build();
                HttpPost request = new HttpPost(this.slackWebhookUrl);
                StringEntity params;
                params = new StringEntity(payload);
                request.addHeader("content-type", "application/x-www-form-urlencoded");
                request.setEntity(params);
                HttpResponse response = httpClient.execute(request);
                // handle response here...
            }
        } catch (JSONException e) {
            // handle exception here
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            // handle exception here
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            // handle exception here
            e.printStackTrace();
        } catch (IOException e) {
            // handle exception here
            e.printStackTrace();
        }
    }
}
