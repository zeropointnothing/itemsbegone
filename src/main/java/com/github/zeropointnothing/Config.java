package com.github.zeropointnothing;

import java.util.List;
import java.util.Objects;

public class Config {
    public Boolean delete_on_deny;
    public final TeamList blacklist;

    public Config(TeamList blacklist, Boolean delete_on_deny) {
        this.blacklist = blacklist;
        this.delete_on_deny = delete_on_deny;
    }

    public static class TeamList {
        public List<TeamConfig> teams;
        public TeamList(List<TeamConfig> default_teams) {
            this.teams = default_teams;
        }

        public TeamConfig getTeam(String name) {
            for (TeamConfig team : teams) {
                if (Objects.equals(team.name, name)) {
                    return team;
                }
            }

            throw new NoSuchTeamException("No such team with name '" + name + "'!");
//            throw new RuntimeException("No such team with name " + name + "!");
        }

        public void addTeam(String name, List<String> blacklist_namespace, List<String> blacklist_item, Boolean enabled) {
            TeamConfig new_team = new TeamConfig(name, blacklist_namespace, blacklist_item, enabled);
            teams.add(new_team);
        }
    }

    public static class TeamConfig {
        public String name;
        public Boolean enabled;
        public List<String> namespace_blacklist;
        public List<String> item_blacklist;

        public TeamConfig(String name, List<String> namespace_blacklist, List<String> item_blacklist, Boolean enabled) {
            if (
                    name == null || enabled == null || namespace_blacklist == null || item_blacklist == null
            ) {
                throw new IllegalArgumentException("TeamConfig can not be initialized with null values!");
            }
            this.name = name;
            this.enabled = enabled;
            this.namespace_blacklist = namespace_blacklist;
            this.item_blacklist = item_blacklist;
        }
    }

    static class NoSuchTeamException extends RuntimeException {
        public NoSuchTeamException(String errorMessage) {
            super(errorMessage);
        }
    }
}
