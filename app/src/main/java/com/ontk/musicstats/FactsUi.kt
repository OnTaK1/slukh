package com.ontk.musicstats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FactsSection(facts: List<Fact>) {
    val stats = facts.filterIsInstance<Fact.Stat>()
    val splits = facts.filterIsInstance<Fact.Split>()
    val lines = facts.filterIsInstance<Fact.Line>()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonoLabel("факты")
        stats.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { FactStat(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        splits.forEach { SplitCard(it) }
        lines.forEach { LineCard(it) }
    }
}

@Composable
private fun FactStat(fact: Fact.Stat, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Panel(modifier = modifier, padding = 14.dp) {
        Box(modifier = Modifier.width(24.dp).height(3.dp).background(cs.primary))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            fact.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            fact.caption,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SplitCard(fact: Fact.Split) {
    val cs = MaterialTheme.colorScheme
    Panel {
        MonoLabel(fact.label)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(fact.leftPercent.coerceAtLeast(1).toFloat())
                    .height(10.dp)
                    .background(cs.primary)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .weight(fact.rightPercent.coerceAtLeast(1).toFloat())
                    .height(10.dp)
                    .background(cs.onBackground.copy(alpha = 0.18f))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${fact.leftName} ${fact.leftPercent}%",
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${fact.rightName} ${fact.rightPercent}%",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(fact.comment, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun LineCard(fact: Fact.Line) {
    Panel {
        MonoLabel(fact.label)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            fact.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}