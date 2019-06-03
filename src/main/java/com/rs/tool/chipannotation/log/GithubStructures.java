package com.rs.tool.chipannotation.log;

import java.util.Date;

public class GithubStructures {

    public class Comment {
        public String url;
        public String html_url;
        public String issue_url;
        public long id;
        public String node_id;
        public User user;
        public Date created_at;
        public Date updated_at;
        public String author_association;
        public String body;
    }

    public static class User {
        public String login;
        public long id;
        public String node_id;
        public String avatar_url;
        public String gravatar_id;
        public String url;
        public String html_url;
        public String followers_url;
        public String following_url;
        public String gists_url;
        public String starred_url;
        public String subscriptions_url;
        public String organizations_url;
        public String repos_url;
        public String events_url;
        public String received_events_url;
        public String type;
        public boolean site_admin;
    }

    public static class CommentBody {
        public String title;
    }

}
