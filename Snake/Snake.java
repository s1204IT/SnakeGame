import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * Console Snake Game — JLine3使用
 * <p>
 * 操作方法:
 *   W / ↑ : 上移動
 *   S / ↓ : 下移動
 *   A / ← : 左移動
 *   D / → : 右移動
 *   R     : リスタート（ゲームオーバー後）
 *   Q     : 終了
 */
class Snake {
    static final int WIDTH = 30; // フィールドの幅
    static final int HEIGHT = 20; // フィールドの高さ
    static final int INITIAL_LENGTH = 4; // 蛇の初期長さ
    static final long TICK_MS = 150; // ループ間隔(ms)

    // セル描画文字（半角2文字で1マス → 縦横比を揃える）
    static final String CELL_EMPTY  = "  ";
    static final String CELL_HEAD   = "@@";
    static final String CELL_BODY   = "##";
    static final String CELL_FOOD   = "()";

    // ANSIエスケープ（画面制御・色）
    static final String HIDE_CURSOR  = "\033[?25l";
    static final String SHOW_CURSOR  = "\033[?25h";
    static final String HOME         = "\033[H";
    static final String ERASE_LINE   = "\033[K";
    static final String RESET        = "\033[0m";
    static final String BOLD         = "\033[1m";
    static final String DIM          = "\033[2m";
    static final String GREEN        = "\033[32m";
    static final String BRIGHT_GREEN = "\033[92m";
    static final String YELLOW       = "\033[93m";
    static final String RED          = "\033[91m";
    static final String CYAN         = "\033[96m";
    static final String WHITE        = "\033[97m";

    // 移動方向
    enum Dir { UP, DOWN, LEFT, RIGHT }

    // ゲーム状態
    static final Deque<int[]> snake = new ArrayDeque<>(); // 蛇セグメント（頭が末尾）
    static Dir dir = Dir.RIGHT; // 現在の進行方向
    static Dir nextDir = Dir.RIGHT; // 次ティックで適用する方向
    static int[] food; // フードの座標
    static int score = 0;
    static boolean running = true;
    static boolean gameOver = false;
    static volatile char lastKey = 0; // 入力スレッドから受け取る最新キー
    static boolean firstRender = true; // 初回のみ画面をクリア
    static Terminal terminal; // JLineターミナル

    // エントリポイント
    public static void main(String[] args) throws Exception {
        terminal = TerminalBuilder.builder().system(true).build();
        terminal.enterRawMode();

        System.out.print(HIDE_CURSOR);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print(SHOW_CURSOR);
            try { terminal.close(); } catch (Exception ignored) {}
        }));

        Thread inputThread = new Thread(Snake::readKeys);
        inputThread.setDaemon(true);
        inputThread.start();

        initGame();

        long lastTick = System.currentTimeMillis();
        while (running) {
            long now = System.currentTimeMillis();
            if (now - lastTick >= TICK_MS) {
                lastTick = now;
                applyInput();
                if (!gameOver) update();
                render();
            }
            Thread.sleep(10);
        }

        System.out.print(SHOW_CURSOR);
        terminal.close();
        System.out.println("Thanks for playing! Final score: " + score);
    }

    // ゲーム初期化
    static void initGame() {
        snake.clear();
        dir = Dir.RIGHT;
        nextDir = Dir.RIGHT;
        score = 0;
        gameOver = false;
        firstRender = true;
        int startX = WIDTH / 2;
        int startY = HEIGHT / 2;
        for (int i = INITIAL_LENGTH - 1; i >= 0; i--)
            snake.addLast(new int[]{startX - i, startY});
        spawnFood();
    }

    // フード生成
    static void spawnFood() {
        Random rng = new Random();
        Set<Long> occupied = new HashSet<>();
        for (int[] seg : snake) occupied.add(encode(seg[0], seg[1]));
        int fx, fy;
        do {
            fx = rng.nextInt(WIDTH);
            fy = rng.nextInt(HEIGHT);
        } while (occupied.contains(encode(fx, fy)));
        food = new int[]{fx, fy};
    }

    // 座標をlongにエンコード（HashSet高速検索用）
    static long encode(int x, int y) { return (long) x * 10000 + y; }

    // キー入力を方向に反映
    static void applyInput() {
        char k = lastKey;
        lastKey = 0;
        switch (k) {
            case 'w': case 'W': case 'A': // A = ESC[A（上矢印）
                if (dir != Dir.DOWN) nextDir = Dir.UP; break;
            case 's': case 'S': case 'B':
                if (dir != Dir.UP) nextDir = Dir.DOWN; break;
            case 'a': case 'D':
                if (dir != Dir.RIGHT) nextDir = Dir.LEFT; break;
            case 'd': case 'C':
                if (dir != Dir.LEFT) nextDir = Dir.RIGHT; break;
            case 'r': case 'R':
                if (gameOver) initGame(); break;
            case 'q': case 'Q':
                running = false; break;
        }
        if (!gameOver) dir = nextDir;
    }

    // 盤面更新
    static void update() {
        int[] head = snake.peekLast();
        int nx = head[0], ny = head[1];
        switch (dir) {
            case UP: ny--; break;
            case DOWN: ny++; break;
            case LEFT: nx--; break;
            case RIGHT: nx++; break;
        }
        if (nx < 0 || nx >= WIDTH || ny < 0 || ny >= HEIGHT) {
            gameOver = true; return;
        }
        for (int[] seg : snake) {
            if (seg[0] == nx && seg[1] == ny) { gameOver = true; return; }
        }
        snake.addLast(new int[]{nx, ny});
        if (nx == food[0] && ny == food[1]) {
            score++;
            spawnFood();
        } else {
            snake.pollFirst();
        }
    }

    // 描画
    static void render() {
        // 文字フィールドを初期化（0=空, 1=胴体, 2=頭, 3=フード）
        int[][] field = new int[HEIGHT][WIDTH];
        field[food[1]][food[0]] = 3;
        for (int[] seg : snake) field[seg[1]][seg[0]] = 1;
        int[] headPos = snake.peekLast();
        if (headPos != null) field[headPos[1]][headPos[0]] = 2;

        StringBuilder sb = new StringBuilder();
        if (firstRender) {
            sb.append("\033[2J");
            firstRender = false;
        }
        sb.append(HOME);

        // ヘッダー
        sb.append(BOLD).append(CYAN).append("  SNAKE GAME").append(RESET);
        sb.append("   ").append(BOLD).append(WHITE).append("SCORE: ")
                .append(YELLOW).append(score).append(RESET).append(ERASE_LINE).append("\r\n");

        // 上壁（各セルが2文字幅なので壁も2倍）
        sb.append(DIM).append(WHITE).append("+");
        for (int x = 0; x < WIDTH; x++) sb.append("--");
        sb.append("+").append(RESET).append(ERASE_LINE).append("\r\n");

        // フィールド各行
        for (int y = 0; y < HEIGHT; y++) {
            sb.append(DIM).append(WHITE).append("|").append(RESET);
            for (int x = 0; x < WIDTH; x++) {
                switch (field[y][x]) {
                    case 2: sb.append(BOLD).append(BRIGHT_GREEN).append(CELL_HEAD).append(RESET); break;
                    case 1: sb.append(GREEN).append(CELL_BODY).append(RESET); break;
                    case 3: sb.append(BOLD).append(YELLOW).append(CELL_FOOD).append(RESET); break;
                    default: sb.append(CELL_EMPTY); break;
                }
            }
            sb.append(DIM).append(WHITE).append("|").append(RESET).append(ERASE_LINE).append("\r\n");
        }

        // 下壁
        sb.append(DIM).append(WHITE).append("+");
        for (int x = 0; x < WIDTH; x++) sb.append("--");
        sb.append("+").append(RESET).append(ERASE_LINE).append("\r\n");

        // フッター
        sb.append(DIM).append("  WASD / Arrow keys: Move   Q: Quit");
        if (gameOver) sb.append("   R: Restart");
        sb.append(RESET).append(ERASE_LINE).append("\r\n");

        // ゲームオーバー表示
        if (gameOver) {
            sb.append("\r\n").append(BOLD).append(RED)
                    .append("  *** GAME OVER ***   Score: ").append(score)
                    .append("   Press R to restart").append(RESET).append(ERASE_LINE).append("\r\n");
        } else {
            sb.append(ERASE_LINE).append("\r\n").append(ERASE_LINE).append("\r\n");
        }

        System.out.print(sb);
    }

    // キー入力スレッド（JLine NonBlockingReaderで即時取得）
    static void readKeys() {
        try {
            NonBlockingReader reader = terminal.reader();
            while (running) {
                int c = reader.read(TICK_MS);
                if (c == NonBlockingReader.READ_EXPIRED) continue;
                if (c == NonBlockingReader.EOF) break;
                if (c == 27) {
                    // ESCシーケンス（矢印キー）: ESC [ A/B/C/D
                    int bracket = reader.read(50);
                    if (bracket == '[') {
                        int arrow = reader.read(50);
                        if (arrow != NonBlockingReader.READ_EXPIRED)
                            lastKey = (char) arrow;
                    }
                } else {
                    lastKey = (char) c;
                }
            }
        } catch (Exception ignored) {}
    }
}