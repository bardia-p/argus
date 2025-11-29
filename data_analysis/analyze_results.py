import yaml
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D

def parse_yaml(file_path):
    with open(file_path, "r") as f:
        data = yaml.safe_load(f)

    rows = []

    for run in data["runs"]:
        date = run["date"]
        world = run["world_id"]

        # Survivors
        for agent, info in run["survivors"].items():
            rows.append({
                "date": date,
                "world_id": world,
                "agent": agent,
                "team": "attacker" if info["aslFile"] == "attacker.asl" else "capitalist",
                "score": info["score"],
                "survived": True
            })

        # Deceased
        for agent, info in run["deceased"].items():
            rows.append({
                "date": date,
                "world_id": world,
                "agent": agent,
                "team": "attacker" if info["aslFile"] == "attacker.asl" else "capitalist",
                "score": info["score"],
                "survived": False
            })

    return pd.DataFrame(rows)
    
def individual_score_plot_for(df, alliance_size):
    df["date"] = pd.to_datetime(df["date"])
    df = df.sort_values("date")
    unique_dates = df["date"].unique()
    date_to_round = {d: i+1 for i, d in enumerate(unique_dates)}
    df["round"] = df["date"].map(date_to_round)

    legend_items = {
        "Live Attackers": "red",
        "Dead Attackers": "#800000",
        "Live Capitalists": "blue",
        "Dead Capitalists": "#000080",
    }

    def get_color(row):
        if row["team"] == "attacker":
            return legend_items["Live Attackers"] if row["survived"] else legend_items["Dead Attackers"]
        else:
            return legend_items["Live Capitalists"] if row["survived"] else legend_items["Dead Capitalists"]
    
    df["color"] = df.apply(get_color, axis=1)
    plt.figure(figsize=(12, 6))
    plt.scatter(df["round"], df["score"], c=df["color"], s=30)

    plt.xticks(range(1, len(unique_dates) + 1))
    plt.xlabel("Round")
    plt.ylabel("Score")
    plt.title("Agent Scores per Round - Alliance Size {}".format(alliance_size))

    legend_handles = [
        Line2D([0], [0], marker="o", color="w", label=label,
            markerfacecolor=color, markersize=8)
        for label, color in legend_items.items()
    ]

    plt.legend(handles=legend_handles, title="Status")
    plt.tight_layout()
    plt.savefig("plots/individual_score_plot_alliance_{}.png".format(alliance_size), dpi=300)

def avg_score_plot_for(df, alliance_size):
    avg_scores = df.groupby("team")["score"].mean()

    fig, ax = plt.subplots()
    bars = ax.bar(avg_scores.index, avg_scores.values, color=['red', 'blue'])

    for bar, avg in zip(bars, avg_scores.values):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2, height + 0.5, f'{avg:.2f}', ha='center', va='bottom')

    ax.set_ylabel("Average Score")
    ax.set_title("Average Score by Team - Alliance Size {}".format(alliance_size))
    plt.savefig("plots/avg_score_plot_alliance_{}.png".format(alliance_size), dpi=300)

def survival_rate_plot_for(df, alliance_size):
    survival_prob = df.groupby("team")["survived"].mean()

    fig, ax = plt.subplots()
    bars = ax.bar(survival_prob.index, survival_prob.values, color=['red', 'blue'])

    for bar, prob in zip(bars, survival_prob.values):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2, height + 0.02, f'{prob:.2f}', ha='center', va='bottom')

    ax.set_ylabel("Probability of Survival")
    ax.set_title("Survival Probability by Team - Alliance Size {}".format(alliance_size))
    ax.set_ylim(0, 1) 

    plt.savefig("plots/survival_rate_plot_alliance_{}.png".format(alliance_size), dpi=300)

def do_plots_for_alliance_size(alliance_size):
    df = parse_yaml("data/game_data_alliance_size_{}.yml".format(alliance_size))
    individual_score_plot_for(df, alliance_size)
    avg_score_plot_for(df, alliance_size)
    survival_rate_plot_for(df, alliance_size)

do_plots_for_alliance_size(1)
do_plots_for_alliance_size(2)
do_plots_for_alliance_size(4)