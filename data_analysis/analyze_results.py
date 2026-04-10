import yaml
import pandas as pd
import matplotlib as mpl
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from scipy import stats

TEAM_COLORS = {
    "attacker": "red",
    "capitalist": "blue",
}

WEAPON_LEVEL = {
    None: 0,
    "null": 0,
    "sword": 1,
    "axe": 2,
    "trident": 3
}

METRIC_LABELS = {
    "woodsChopped": "Wood Chopped",
    "woodsDonated": "Wood Donated",
    "numHouses": "Number of Houses",
    "numHitsOnZombies": "Hits on Zombies",
    "weapon_level": "Weapon (0=None, 1=Sword, 2=Axe, 3=Trident)"
}

mpl.rcParams.update({
    "font.size": 11,
    "axes.titlesize": 12,
    "axes.labelsize": 11,
    "xtick.labelsize": 10,
    "ytick.labelsize": 10,
    "legend.fontsize": 10,
    "figure.dpi": 300,
})
def parse_yaml(file_path):
    with open(file_path, "r") as f:
        data = yaml.safe_load(f)

    rows = []

    for run in data["runs"]:
        date = run["date"]
        world = run["world_id"]

        def process(agent_dict, survived):
            for agent, info in agent_dict.items():
                weapon = info["weapon"]
                rows.append({
                    "date": date,
                    "world_id": world,
                    "agent": agent,
                    "team": "attacker" if info["aslFile"] == "attacker.asl" else "capitalist",
                    "score": info["score"],
                    "weapon": weapon,
                    "weapon_level": WEAPON_LEVEL.get(weapon, 0),
                    "woodsChopped": info["woodsChopped"],
                    "numHouses": info["numHouses"],
                    "woodsDonated": info["woodsDonated"],
                    "numHitsOnZombies": info["numHitsOnZombies"],
                    "survived": survived
                })

        process(run["survivors"], True)
        process(run["deceased"], False)

    return pd.DataFrame(rows)

def performance_plot(df, alliance_size):
    df = df.copy()
    df["effective_score"] = df["score"] * df["survived"].astype(int)

    per_run = (
        df.groupby(["date", "world_id", "team"])
        .agg(
            score=("effective_score", "mean"),
            survival=("survived", "mean")
        )
        .reset_index()
    )

    fig, ax = plt.subplots(figsize=(4.5, 4))

    X_RANGE = 1.0
    Y_RANGE = per_run["score"].max() - per_run["score"].min()
    BOX_THICKNESS = 0.03
    x_thickness = X_RANGE * BOX_THICKNESS
    y_thickness = Y_RANGE * BOX_THICKNESS

    def ci95(data):
        n = len(data)
        mean = data.mean()
        if n < 2:
            return mean, mean, mean
        se = stats.sem(data)
        margin = se * stats.t.ppf(0.975, df=n - 1)
        return mean, mean - margin, mean + margin

    for team in per_run["team"].unique():
        subset = per_run[per_run["team"] == team]
        x = subset["survival"].values
        y = subset["score"].values
        color = TEAM_COLORS[team]

        x_mean, x_lo, x_hi = ci95(x)
        y_mean, y_lo, y_hi = ci95(y)

        x_lo = max(0.0, x_lo)
        x_hi = min(1.0, x_hi)

        lw = 1.5
        kw = dict(linewidth=lw, edgecolor=color, facecolor=color, alpha=0.4, zorder=3)

        ax.add_patch(mpatches.Rectangle(
            (x_lo, y_mean - y_thickness / 2), x_hi - x_lo, y_thickness, **kw
        ))
        ax.plot([x_mean, x_mean], [y_mean - y_thickness/2, y_mean + y_thickness/2],
                color=color, lw=lw + 1, zorder=4)

        ax.add_patch(mpatches.Rectangle(
            (x_mean - x_thickness / 2, y_lo), x_thickness, y_hi - y_lo, **kw
        ))
        ax.plot([x_mean - x_thickness/2, x_mean + x_thickness/2], [y_mean, y_mean],
                color=color, lw=lw + 1, zorder=4)

    ax.set_xlim(0, 1)
    ax.set_ylim(bottom=0)
    ax.set_xlabel("Survival Rate")
    ax.set_ylabel("Avg Score per Agent")
    ax.set_title(f"Performance Trade-off - Alliance {alliance_size}")

    legend_handles = [
        mpatches.Patch(facecolor=TEAM_COLORS[t], edgecolor=TEAM_COLORS[t],
                       alpha=0.7, label=t)
        for t in per_run["team"].unique()
    ]
    legend_handles.append(
        mpatches.Patch(facecolor="gray", edgecolor="gray", alpha=0.4,
                       label="95% CI (t-dist)")
    )
    ax.legend(handles=legend_handles)

    plt.tight_layout(pad=0.5)
    plt.savefig(f"plots/performance_plot_alliance_{alliance_size}.png", dpi=300, 
                bbox_inches="tight", pad_inches=0.05)

def behaviour_plot(df, alliance_size):
    metrics = [
        "woodsChopped",
        "woodsDonated",
        "numHouses",
        "weapon_level"
    ]

    per_run = df.groupby(["date", "world_id", "team"])[metrics].mean().reset_index()
    teams = per_run["team"].unique()

    n_cols = 2
    n_rows = (len(metrics) + n_cols - 1) // n_cols

    fig, axes = plt.subplots(n_rows, n_cols, figsize=(3.2*n_cols, 2.8*n_rows))
    axes = axes.flatten()

    for i, metric in enumerate(metrics):
        ax = axes[i]
        data = [per_run[per_run["team"] == t][metric] for t in teams]

        box = ax.boxplot(
            data,
            tick_labels=teams,
            patch_artist=True,
            showmeans=True
        )

        for patch, t in zip(box["boxes"], teams):
            patch.set_facecolor(TEAM_COLORS[t])

        ax.set_title(METRIC_LABELS[metric], fontsize=10)
        ax.tick_params(axis='x', labelrotation=25)
        ax.set_ylim(bottom=0)

    # Hide any unused subplots
    for j in range(len(metrics), len(axes)):
        fig.delaxes(axes[j])

    fig.suptitle(f"Behaviour Distributions - Alliance {alliance_size}", fontsize=12)
    plt.tight_layout(pad=0.4)
    plt.subplots_adjust(top=0.92, hspace=0.5, wspace=0.38)
    plt.savefig(f"plots/behaviour_plot_alliance_{alliance_size}.png", dpi=300, 
                bbox_inches="tight", pad_inches=0.05)

def run(alliance_size):
    df = parse_yaml(f"data/game_data_alliance_size_{alliance_size}.yml")

    performance_plot(df, alliance_size)
    behaviour_plot(df, alliance_size)

run(1)
run(2)
run(4)