package code.opentok;


import com.opentok.OpenTok;
import com.opentok.Session;
import com.opentok.TokenOptions;
import com.opentok.Role;
import net.liftweb.util.Props;
import com.opentok.exception.OpenTokException;
import com.opentok.MediaMode;
import com.opentok.SessionProperties;

/**
 * Created by markom on 5/22/16.
 */
public class OpenTokUtil extends Exception {
    private static Session session;

    public OpenTokUtil() {
        // Empty constructor
    }

    public static OpenTok createOpenTok() {
        // Set the following constants with the API key and API secret
        // that you receive when you sign up to use the OpenTok API:
        int apiKey = Integer.parseInt(Props.get("meeting.tokbox_api_key", "0000"));
        String apiSecret = Props.get("meeting.tokbox_api_secret", "YOUR API SECRET");
        OpenTok opentok = new OpenTok(apiKey, apiSecret);
        return opentok;
    }

    public static Session getSession() throws OpenTokException {
        if(session == null){
            // A session that uses the OpenTok Media Router:
            session = createOpenTok().createSession(new SessionProperties.Builder()
                    .mediaMode(MediaMode.ROUTED)
                    .build());
        }
        return session;
    }

    public static String generateTokenForModerator(int expireTimeInMinutes) throws OpenTokException  {

        // Generate a token. Use the Role MODERATOR. Expire time is defined by parameter expireTimeInMinutes.
        String token = session.generateToken(new TokenOptions.Builder()
                .role(Role.MODERATOR)
                .expireTime((System.currentTimeMillis() / 1000L) + (expireTimeInMinutes * 60)) // in expireTimeInMinutes
                .data("name=Simon")
                .build());

        return token;
    }

    public static String generateTokenForPublisher(int expireTimeInMinutes) throws OpenTokException  {

        // Generate a token. Use the Role PUBLISHER. Expire time is defined by parameter expireTimeInMinutes.
        String token = session.generateToken(new TokenOptions.Builder()
                .role(Role.PUBLISHER)
                .expireTime((System.currentTimeMillis() / 1000L) + (expireTimeInMinutes * 60)) // in expireTimeInMinutes
                .data("name=Simon")
                .build());

        return token;
    }


}
