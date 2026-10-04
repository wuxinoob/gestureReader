package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BookArticle
import com.example.data.SampleBooks
import com.example.model.ActionType
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ReaderThemeDarkBg
import com.example.ui.theme.ReaderThemeDarkText
import com.example.ui.theme.ReaderThemeGreenBg
import com.example.ui.theme.ReaderThemeGreenText
import com.example.ui.theme.ReaderThemePaperBg
import com.example.ui.theme.ReaderThemePaperText
import com.example.ui.theme.ReaderThemeSepiaBg
import com.example.ui.theme.ReaderThemeSepiaText
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class ReaderThemeMode(val label: String, val bg: Color, val text: Color) {
    PAPER("纸书柔白", ReaderThemePaperBg, ReaderThemePaperText),
    GREEN("护眼墨绿", ReaderThemeGreenBg, ReaderThemeGreenText),
    SEPIA("复古羊皮", ReaderThemeSepiaBg, ReaderThemeSepiaText),
    DARK("暗夜沉浸", ReaderThemeDarkBg, ReaderThemeDarkText)
}

@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedBookIndex by remember { mutableIntStateOf(0) }
    val currentBook = SampleBooks.articles[selectedBookIndex]

    var currentParagraphPage by remember { mutableIntStateOf(0) }
    var fontSizeSp by remember { mutableIntStateOf(18) }
    var currentTheme by remember { mutableStateOf(ReaderThemeMode.DARK) }
    var showMenu by remember { mutableStateOf(false) }

    val recognitionResult by viewModel.recognitionResult.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 监听在应用内触发的动作 (如低头下翻、抬头回滚)
    LaunchedEffect(recognitionResult.lastFiredTimestamp) {
        val action = recognitionResult.lastFiredAction
        if (action != null && recognitionResult.lastFiredTimestamp > 0) {
            when (action) {
                ActionType.SWIPE_UP, ActionType.SWIPE_LEFT, ActionType.SCROLL_DOWN_SLOW -> {
                    // 下一页
                    if (currentParagraphPage < currentBook.paragraphs.size - 1) {
                        currentParagraphPage++
                    }
                }
                ActionType.SWIPE_DOWN, ActionType.SWIPE_RIGHT, ActionType.SCROLL_UP_SLOW -> {
                    // 上一页
                    if (currentParagraphPage > 0) {
                        currentParagraphPage--
                    }
                }
                ActionType.CLICK_CENTER -> {
                    showMenu = !showMenu
                }
                else -> {}
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.bg)
            .clickable { showMenu = !showMenu }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部迷你状态指示器 (实时提示手势识别)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "无障碍翻页体验中: 低头/左划下翻, 抬头/右划上翻",
                        fontSize = 11.sp,
                        color = currentTheme.text.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = "${currentParagraphPage + 1} / ${currentBook.paragraphs.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.text.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 书籍正文卡片
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentBook.title,
                    fontSize = (fontSizeSp + 4).sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.text,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "—— ${currentBook.author}",
                    fontSize = (fontSizeSp - 4).sp,
                    color = currentTheme.text.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Text(
                    text = currentBook.paragraphs.getOrElse(currentParagraphPage) { "" },
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.65f).sp,
                    color = currentTheme.text,
                    fontFamily = FontFamily.Serif
                )
            }

            // 底部翻页提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        if (currentParagraphPage > 0) currentParagraphPage--
                    },
                    enabled = currentParagraphPage > 0,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "上一页", fontSize = 12.sp)
                }

                Text(
                    text = "点击屏幕中央切换工具栏",
                    fontSize = 11.sp,
                    color = currentTheme.text.copy(alpha = 0.45f)
                )

                FilledTonalButton(
                    onClick = {
                        if (currentParagraphPage < currentBook.paragraphs.size - 1) currentParagraphPage++
                    },
                    enabled = currentParagraphPage < currentBook.paragraphs.size - 1,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "下一页", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        // 浮层菜单栏 (点击呼出)
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 文章选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SampleBooks.articles.forEachIndexed { index, book ->
                            val isSelected = selectedBookIndex == index
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBookIndex = index
                                    currentParagraphPage = 0
                                },
                                label = { Text(text = book.title.take(6) + "..", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    // 字号调节
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "字号大小", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { if (fontSizeSp > 14) fontSizeSp -= 2 },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("A-")
                            }
                            Text(
                                text = "${fontSizeSp}sp",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            FilledTonalButton(
                                onClick = { if (fontSizeSp < 28) fontSizeSp += 2 },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("A+")
                            }
                        }
                    }

                    // 配色主题
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderThemeMode.entries.forEach { mode ->
                            val isSelected = currentTheme == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(mode.bg)
                                    .clickable { currentTheme = mode }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = mode.text
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
