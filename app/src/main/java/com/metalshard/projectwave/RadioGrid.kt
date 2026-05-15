package com.metalshard.projectwave

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioCard(station: RadioStation, onClick: () -> Unit, onLongClick: () -> Unit) {
    val placeholder = rememberVectorPainter(Icons.Default.Radio)

    OutlinedCard(
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = station.imageUrl,
                contentDescription = station.name,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )
            Text(
                text = station.name,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RadioGrid(
    modifier: Modifier,
    stations: List<RadioStation>,
    onStationSelected: (RadioStation) -> Unit,
    onStationLongClick: (RadioStation) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(stations) { station ->
            RadioCard(
                station = station,
                onClick = { onStationSelected(station) },
                onLongClick = { onStationLongClick(station) }
            )
        }
    }
}