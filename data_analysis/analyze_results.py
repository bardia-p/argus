import yaml
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D

TEAM_COLORS = {
    "attacker": "red",
    "capitalist": "blue",
}

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
    df = df.copy()
    df["effective_score"] = df["score"] * df["survived"].astype(int)

    score_per_run = (
        df
        .groupby(["date", "world_id", "team"])["effective_score"]
        .mean()
        .reset_index(name="avg_score_per_agent")
    )

    stats = (
        score_per_run
        .groupby("team")["avg_score_per_agent"]
        .agg(["mean", "std"])
    )

    fig, ax = plt.subplots()
    bars = ax.bar(
        stats.index,
        stats["mean"],
        yerr=stats["std"],
        capsize=6,
        color=[TEAM_COLORS[t] for t in stats.index]
    )

    ax.set_ylabel("Average Score per Agent (dead = 0)")
    ax.set_title(f"Normalized Team Score - Alliance Size {alliance_size}")

    for bar, mean in zip(bars, stats["mean"]):
        ax.text(
            bar.get_x() + bar.get_width() / 2 - 0.1,
            bar.get_height(),
            f"{mean:.1f}",
            ha="center",
            va="bottom"
        )

    plt.tight_layout()
    plt.savefig(
        f"plots/avg_score_plot_alliance_{alliance_size}.png",
        dpi=300
    )

def survival_rate_plot_for(df, alliance_size):
    survival_per_run = (
        df
        .groupby(["date", "world_id", "team"])["survived"]
        .mean()
        .reset_index(name="survival_rate")
    )

    stats = (
        survival_per_run
        .groupby("team")["survival_rate"]
        .agg(["mean", "std"])
    )

    fig, ax = plt.subplots()
    bars = ax.bar(
        stats.index,
        stats["mean"],
        yerr=stats["std"],
        capsize=6,
        color=[TEAM_COLORS[t] for t in stats.index]
    )

    ax.set_ylabel("Survival Probability")
    ax.set_title(f"Survival Probability by Team - Alliance Size {alliance_size}")
    ax.set_ylim(0, 1)

    for bar, mean in zip(bars, stats["mean"]):
        ax.text(
            bar.get_x() + bar.get_width() / 2 - 0.1,
            bar.get_height(),
            f"{mean:.2f}",
            ha="center",
            va="bottom"
        )

    plt.tight_layout()
    plt.savefig(
        f"plots/survival_rate_plot_alliance_{alliance_size}.png",
        dpi=300
    )

def print_stats_for(df, alliance_size):
    print(f"\n=== Alliance Size {alliance_size} (Run-level, normalized) ===")

    df = df.copy()
    df["effective_score"] = df["score"] * df["survived"].astype(int)

    # ---- SCORE: per-run average score per agent (dead = 0) ----
    score_per_run = (
        df
        .groupby(["date", "world_id", "team"])["effective_score"]
        .mean()
        .reset_index(name="avg_score_per_agent")
    )

    score_stats = (
        score_per_run
        .groupby("team")["avg_score_per_agent"]
        .agg(
            score_mean="mean",
            score_std="std"
        )
    )

    # ---- SURVIVAL: per-run survival rate ----
    survival_per_run = (
        df
        .groupby(["date", "world_id", "team"])["survived"]
        .mean()
        .reset_index(name="survival_rate")
    )

    survival_stats = (
        survival_per_run
        .groupby("team")["survival_rate"]
        .agg(
            survival_mean="mean",
            survival_std="std"
        )
    )

    stats = score_stats.join(survival_stats)

    print(stats.round(3))
    return stats


def do_plots_for_alliance_size(alliance_size):
    df = parse_yaml("data/game_data_alliance_size_{}.yml".format(alliance_size))
    
    print_stats_for(df, alliance_size)

    individual_score_plot_for(df, alliance_size)
    avg_score_plot_for(df, alliance_size)
    survival_rate_plot_for(df, alliance_size)

do_plots_for_alliance_size(1)
do_plots_for_alliance_size(2)
do_plots_for_alliance_size(4)