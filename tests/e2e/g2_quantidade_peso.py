"""
Automacao E2E do G2 "Quantidade / multiplicador e peso na venda" (pyautogui).

Roteiro de origem: tests/e2e/roteiros/G2_quantidade_peso.md (parecer do John).

ESCOPO DELIBERADAMENTE PEQUENO (pyautogui e fragil por natureza) -- os 4 fluxos
de ALTO valor pedidos na "Especificacao da automacao Python" do roteiro:
  Fluxo A -> Cenario 1 : UN 6x       -> `6*7891234`  => TOTAL R$ 30,00.
  Fluxo B -> Cenario 2 : KG 1,250    -> `1,250*7899` => TOTAL R$ 7,50.
  Fluxo C -> Cenario 4 : somatorio RN07 -> `6*7891234` + `7891234` (uma so linha)
             => TOTAL R$ 35,00 (Qtd 7, nao duplica).
  Fluxo D -> Cenario 5 : UN com fracao recusada, venda NAO cai ->
             `2,5*7891234` => status "nao aceita fracao" e TOTAL segue R$ 0,00;
             depois `6*7891234` => R$ 30,00 (o erro nao derrubou a venda).

Cenarios 3, 6 e 7 do roteiro ficam para validacao MANUAL (ver roteiro): multiplicar
cenarios de erro no pyautogui so aumenta a fragilidade sem ganho proporcional.

Principios (mesmos do g1_senha_supervisor.py, ao lado):
  - Teclado-first: TUDO e digitacao no campo do leitor + Enter. NENHUM clique por
    pixel -- combina com o fluxo da venda e e bem menos fragil.
  - Verificacao e SEMPRE pela TELA (OCR de regioes fixas). NUNCA le o banco.
  - Esperas EXPLICITAS entre passos, cada uma com o porque no comentario.
  - Qualquer falha de verificacao ABORTA dizendo exatamente qual passo quebrou.

RESET ENTRE FLUXOS (por que F2): cada fluxo comeca com a venda LIMPA. O atalho F2
(menu "Vender", MainController) re-navega para a tela de Venda; o initialize() do
SaleController SEMPRE abre uma venda nova e vazia (openSale). Logo, re-apertar F2
zera a venda sem precisar de gerente -- diferente do g1, o G2 nao precisa de
cancelamento com supervisor para resetar.

ISTO E FERRAMENTA DE TESTE, NAO CODIGO DE PRODUCAO.
Este script NAO instala nada, NAO sobe o app e NAO mexe no banco. Quem prepara o
ambiente e roda e o DEV (abrir o app desktop e acao de ambiente). Veja o README.md
ao lado para pre-condicoes e seeds (tests/e2e/seed_g2.sql).
"""

from __future__ import annotations

import sys
import time
import unicodedata
from dataclasses import dataclass
from typing import NoReturn

# ---------------------------------------------------------------------------
# Dependencias opcionais: importadas com mensagem clara se faltarem.
# O script NAO instala nada; o README explica o `pip install`.
# ---------------------------------------------------------------------------
try:
    import pyautogui
except ImportError:  # pragma: no cover - ambiente do dev
    print(
        "ERRO: biblioteca 'pyautogui' nao encontrada.\n"
        "Instale no ambiente do DEV (fora deste script): pip install pyautogui\n"
        "Veja tests/e2e/README.md.",
        file=sys.stderr,
    )
    sys.exit(2)

try:
    import pytesseract
    from PIL import Image  # noqa: F401  (usado indiretamente por pyautogui.screenshot)

    _OCR_DISPONIVEL = True
except ImportError:  # pragma: no cover - ambiente do dev
    _OCR_DISPONIVEL = False


# ===========================================================================
# CONFIGURACAO (ajuste aqui, nao espalhe "numeros magicos" pelo codigo)
# ===========================================================================

# Resolucao em que as regioes de OCR abaixo foram calibradas (mesma do g1, pois
# e a mesma tela de venda, com os mesmos rotulos TOTAL e status). Se a maquina do
# dev usar outra resolucao/escala, recalibre REGIAO_* e rode de novo.
RESOLUCAO_ESPERADA = (1920, 1080)
# Se a resolucao real diferir, aborta (True) ou so avisa e segue (False).
EXIGIR_RESOLUCAO_EXATA = True

# --- Credenciais e dados de SEED (os mesmos do roteiro; NAO sao senhas de
# producao). Operador criado via UserService/BCrypt; produtos via seed_g2.sql. ---
OPERADOR_LOGIN = "caixa1"
OPERADOR_SENHA = "1234"

# Produtos de seed (ver tests/e2e/seed_g2.sql). Codigo de barras e String!
PRODUTO_UN_CODIGO = "7891234"   # Refrigerante Lata 350ml, R$ 5,00, UNIDADE
PRODUTO_KG_CODIGO = "7899"      # Banana Prata (granel), R$ 6,00/kg, PESO

# O caixa de caixa1 ja deve estar ABERTO antes de rodar (deixe o banco nesse
# estado; abra pela UI uma vez). Abrir caixa pela UI a cada run e fragil e varia
# de tela, por isso fica fora do escopo automatizado -- igual ao g1.
CAIXA_JA_ABERTO = True

# --- Navegacao por teclado --------------------------------------------------
# Atalho do menu para ir/voltar a tela de Venda (F2 = onVender no MainController).
# Re-apertar F2 recarrega a tela de venda => initialize abre uma venda NOVA/VAZIA.
ATALHO_TELA_VENDA = "f2"

# --- Esperas EXPLICITAS (segundos). Cada uma documenta o porque. -----------
ESPERA_APOS_TECLA = 0.25        # deixa o JavaFX processar o evento de tecla
ESPERA_ABRIR_TELA = 2.0         # troca de tela/scene do shell nao e instantanea
ESPERA_APOS_BIPAR = 0.6         # service grava item + recalcula total + cupom
ESPERA_LOGIN = 3.0              # autenticar + montar o shell principal

# --- Timeouts e poll para as verificacoes por OCR --------------------------
TIMEOUT_VERIFICACAO = 8.0       # tempo maximo esperando um texto aparecer
INTERVALO_POLL = 0.5            # frequencia de releitura da regiao

# --- Regioes de tela (left, top, width, height) calibradas para 1920x1080,
# janela MAXIMIZADA, tema/CSS padrao. MESMAS do g1 (mesma tela de venda).
# Recalibre na maquina do dev se mudar resolucao/escala/tema. ---------------
REGIAO_TOTAL = (1540, 250, 320, 90)          # rotulo gigante "R$ x,xx" (RNF04)
REGIAO_STATUS = (24, 560, 900, 40)           # statusLabel abaixo da tabela
REGIAO_TABELA_ITENS = (24, 300, 1100, 250)   # area da TableView (linhas)

# Textos esperados na tela (comparacao normalizada: minusculas, sem acento,
# espacos colapsados; para TOTAIS, separador '.'/',' e unificado -- ver abaixo).
TOTAL_ZERO = "R$ 0,00"
TOTAL_30 = "R$ 30,00"      # 6 un x R$ 5,00  (fluxo A e D final)
TOTAL_7_50 = "R$ 7,50"     # 1,250 kg x R$ 6,00 (fluxo B)
TOTAL_35 = "R$ 35,00"      # 7 un x R$ 5,00  (fluxo C: somatorio RN07)
TEXTO_PRODUTO_UN = "Refrigerante"
TEXTO_PRODUTO_KG = "Banana"
# Mensagem do service (RN05): "Produto vendido por unidade nao aceita fracao".
# Casamos um trecho robusto, ja sem acento (a normalizacao tira os acentos).
STATUS_UN_FRACAO = "nao aceita fra"


# ===========================================================================
# INFRAESTRUTURA (helpers pequenos: espera, teclado, OCR, asserts)
# ===========================================================================

class FalhaDeTeste(Exception):
    """Erro de verificacao: o fluxo nao se comportou como o roteiro manda."""


@dataclass
class ResultadoFluxo:
    nome: str
    passou: bool
    detalhe: str


def abortar(passo: str, motivo: str) -> "NoReturn":
    """Interrompe o fluxo atual dizendo QUAL passo quebrou e por que."""
    raise FalhaDeTeste(f"[{passo}] {motivo}")


def esperar(segundos: float, motivo: str) -> None:
    """Espera explicita e comentada (o 'motivo' documenta o porque no log)."""
    print(f"    ... esperando {segundos:.2f}s ({motivo})")
    time.sleep(segundos)


def _tirar_acentos(texto: str) -> str:
    """Remove acentos para o OCR nao precisar acertar 'a' vs 'a-til' etc."""
    decomposto = unicodedata.normalize("NFKD", texto)
    return "".join(ch for ch in decomposto if not unicodedata.combining(ch))


def _normalizar(texto: str) -> str:
    """Minusculas + sem acento + espacos colapsados (comparacao tolerante)."""
    return " ".join(_tirar_acentos(texto).lower().split())


def _normalizar_valor(texto: str) -> str:
    """Como _normalizar, mas unifica o separador decimal.

    O rotulo TOTAL e montado por `"R$ " + BigDecimal`, entao a tela mostra PONTO
    ("R$ 30.00"), enquanto o roteiro fala em VIRGULA ("R$ 30,00"); alem disso o
    Tesseract confunde '.' e ',' com frequencia. Mapeando ambos para '.' a
    comparacao de valores monetarios fica imune a essa ambiguidade.
    """
    return _normalizar(texto).replace(",", ".")


def digitar_texto(texto: str) -> None:
    """Digita texto no campo em foco; 'interval' evita perder caracteres."""
    pyautogui.typewrite(texto, interval=0.04)
    time.sleep(ESPERA_APOS_TECLA)


def pressionar(tecla: str, vezes: int = 1) -> None:
    """Pressiona uma tecla N vezes, com micro-pausa para o JavaFX acompanhar."""
    for _ in range(vezes):
        pyautogui.press(tecla)
        time.sleep(ESPERA_APOS_TECLA)


def ler_texto_da_regiao(regiao: tuple[int, int, int, int]) -> str:
    """Captura a regiao da tela e devolve o texto lido por OCR."""
    if not _OCR_DISPONIVEL:
        abortar(
            "OCR",
            "pytesseract/Pillow ausentes ou Tesseract nao instalado. "
            "Veja o README (secao Verificacao por OCR).",
        )
    captura = pyautogui.screenshot(region=regiao)
    try:
        return pytesseract.image_to_string(captura, lang="por")
    except Exception as erro:  # engine ausente/ma config -> mensagem clara
        abortar("OCR", f"falha ao executar o Tesseract: {erro}")


def _esperar_condicao_na_regiao(
    regiao: tuple[int, int, int, int],
    alvo_normalizado: str,
    deve_estar_presente: bool,
    normalizador,
    passo: str,
    timeout: float,
) -> None:
    """Faz poll da regiao ate o alvo estar presente/ausente conforme pedido."""
    prazo = time.time() + timeout
    ultimo_lido = ""
    while time.time() < prazo:
        ultimo_lido = normalizador(ler_texto_da_regiao(regiao))
        presente = alvo_normalizado in ultimo_lido
        if presente == deve_estar_presente:
            marca = "contem" if deve_estar_presente else "nao contem"
            print(f"    OK [{passo}] regiao {marca} o alvo esperado")
            return
        time.sleep(INTERVALO_POLL)
    relacao = "ver" if deve_estar_presente else "NAO ver"
    abortar(
        passo,
        f"esperava {relacao} '{alvo_normalizado}' na regiao {regiao}, "
        f"mas o OCR leu '{ultimo_lido}' ate o timeout de {timeout:.0f}s.",
    )


def esperar_texto_na_regiao(
    regiao: tuple[int, int, int, int],
    esperado: str,
    passo: str,
    timeout: float = TIMEOUT_VERIFICACAO,
) -> None:
    """Faz poll da regiao ate 'esperado' aparecer (texto comum, sem acento)."""
    _esperar_condicao_na_regiao(
        regiao, _normalizar(esperado), True, _normalizar, passo, timeout
    )


def esperar_texto_ausente_na_regiao(
    regiao: tuple[int, int, int, int],
    nao_esperado: str,
    passo: str,
    timeout: float = TIMEOUT_VERIFICACAO,
) -> None:
    """Faz poll da regiao ate 'nao_esperado' sumir (texto comum, sem acento)."""
    _esperar_condicao_na_regiao(
        regiao, _normalizar(nao_esperado), False, _normalizar, passo, timeout
    )


def esperar_total_na_regiao(
    esperado: str,
    passo: str,
    timeout: float = TIMEOUT_VERIFICACAO,
) -> None:
    """Confere o rotulo TOTAL com separador decimal tolerante ('.'/',')."""
    _esperar_condicao_na_regiao(
        REGIAO_TOTAL, _normalizar_valor(esperado), True, _normalizar_valor,
        passo, timeout,
    )


def confirmar_total_estavel(
    esperado: str,
    passo: str,
    duracao: float = 2.0,
) -> None:
    """Confirma que o TOTAL PERMANECE no valor esperado por 'duracao' segundos.

    Usado quando o importante e provar que NADA mudou (ex.: fracao recusada nao
    altera o TOTAL). Le varias vezes e exige que todas batam com o esperado.
    """
    alvo = _normalizar_valor(esperado)
    prazo = time.time() + duracao
    while time.time() < prazo:
        lido = _normalizar_valor(ler_texto_da_regiao(REGIAO_TOTAL))
        if alvo not in lido:
            abortar(
                passo,
                f"esperava que o TOTAL continuasse '{esperado}', "
                f"mas o OCR leu '{lido}'.",
            )
        time.sleep(INTERVALO_POLL)
    print(f"    OK [{passo}] TOTAL permaneceu em '{esperado}'")


def conferir_resolucao() -> None:
    """Confere a resolucao antes de tudo (todas as regioes dependem dela)."""
    largura, altura = pyautogui.size()
    print(f"Resolucao detectada: {largura}x{altura}")
    if (largura, altura) != RESOLUCAO_ESPERADA:
        mensagem = (
            f"resolucao {largura}x{altura} difere da calibrada "
            f"{RESOLUCAO_ESPERADA[0]}x{RESOLUCAO_ESPERADA[1]}; "
            "as regioes de OCR podem nao bater."
        )
        if EXIGIR_RESOLUCAO_EXATA:
            abortar("resolucao", mensagem)
        print(f"AVISO: {mensagem}")


# ===========================================================================
# ACOES DE ALTO NIVEL (login, abrir/reiniciar venda, bipar no leitor)
# ===========================================================================

def logar(login: str, senha: str) -> None:
    """Login na tela inicial (usuario ja focado -> Tab -> senha -> Enter)."""
    print(f"  Login como '{login}'")
    digitar_texto(login)
    pressionar("tab")
    digitar_texto(senha)
    pressionar("enter")
    esperar(ESPERA_LOGIN, "autenticar e montar o shell principal")


def ir_para_tela_de_venda() -> None:
    """Navega (F2) para a tela de Venda e ancora pelo rotulo de TOTAL."""
    print("  Indo para a tela de Venda (F2)")
    pressionar(ATALHO_TELA_VENDA)
    esperar(ESPERA_ABRIR_TELA, "trocar o centro do shell para a tela de venda")
    # A tela de venda sempre mostra "R$"; serve de ancora de que chegou.
    esperar_texto_na_regiao(REGIAO_TOTAL, "R$", "abrir tela de venda")


def reiniciar_venda_pelo_menu() -> None:
    """Zera a venda re-navegando pelo menu (F2): initialize abre venda vazia.

    Nao precisa de gerente (diferente do g1): basta recarregar a tela de venda.
    """
    print("  Reiniciando a venda (F2 -> nova venda vazia)")
    pressionar(ATALHO_TELA_VENDA)
    esperar(ESPERA_ABRIR_TELA, "recarregar a tela de venda e abrir venda vazia")
    esperar_total_na_regiao(TOTAL_ZERO, "reset total 0,00")


def bipar_no_leitor(entrada: str, motivo: str) -> None:
    """Digita 'entrada' (ex.: '6*7891234' ou '1,250*7899') no leitor + Enter.

    Faz Ctrl+A antes de digitar para substituir qualquer residuo no campo, e
    depois do Enter aguarda o service gravar o item e recalcular o TOTAL.
    """
    print(f"  Leitor: digitando '{entrada}' + Enter ({motivo})")
    pyautogui.hotkey("ctrl", "a")      # seleciona tudo -> a digitacao substitui
    time.sleep(ESPERA_APOS_TECLA)
    digitar_texto(entrada)
    pressionar("enter")
    esperar(ESPERA_APOS_BIPAR, "gravar item e recalcular o total/status")


# ===========================================================================
# FLUXOS COBERTOS (um metodo por fluxo, verificacoes embutidas)
# ===========================================================================

def fluxo_a_un_multiplicador() -> ResultadoFluxo:
    """Cenario 1: UN 6x num Enter so (`6*7891234`) => TOTAL R$ 30,00."""
    nome = "Fluxo A - UN 6x multiplicador (Cenario 1)"
    print(f"\n=== {nome} ===")

    bipar_no_leitor(f"6*{PRODUTO_UN_CODIGO}", "6 unidades de refrigerante")
    esperar_texto_na_regiao(REGIAO_TABELA_ITENS, TEXTO_PRODUTO_UN, "A linha do produto")
    esperar_total_na_regiao(TOTAL_30, "A total 30,00")
    return ResultadoFluxo(nome, True, "6*codigo UN => uma linha, TOTAL R$ 30,00")


def fluxo_b_kg_peso() -> ResultadoFluxo:
    """Cenario 2: KG 1,250 (`1,250*7899`) => TOTAL R$ 7,50."""
    nome = "Fluxo B - KG 1,250 peso (Cenario 2)"
    print(f"\n=== {nome} ===")

    bipar_no_leitor(f"1,250*{PRODUTO_KG_CODIGO}", "1,250 kg de banana")
    esperar_texto_na_regiao(REGIAO_TABELA_ITENS, TEXTO_PRODUTO_KG, "B linha do produto")
    esperar_total_na_regiao(TOTAL_7_50, "B total 7,50")
    return ResultadoFluxo(nome, True, "1,250*codigo KG => TOTAL R$ 7,50")


def fluxo_c_somatorio_rn07() -> ResultadoFluxo:
    """Cenario 4: `6*7891234` depois `7891234` somam na mesma linha => R$ 35,00."""
    nome = "Fluxo C - somatorio RN07 (Cenario 4)"
    print(f"\n=== {nome} ===")

    bipar_no_leitor(f"6*{PRODUTO_UN_CODIGO}", "6 unidades")
    esperar_total_na_regiao(TOTAL_30, "C1 total 30,00")

    bipar_no_leitor(PRODUTO_UN_CODIGO, "mais 1 unidade (bipe simples) do mesmo produto")
    # TOTAL R$ 35,00 = 7 x R$ 5,00: prova que somou na mesma linha (RN07),
    # sem criar linha duplicada (6 + 1, nao duas linhas de 6 e 1).
    esperar_total_na_regiao(TOTAL_35, "C2 total 35,00 (RN07, Qtd 7)")
    esperar_texto_na_regiao(REGIAO_TABELA_ITENS, TEXTO_PRODUTO_UN, "C2 linha unica")
    return ResultadoFluxo(nome, True, "6x + bipe simples => uma linha, TOTAL R$ 35,00")


def fluxo_d_un_fracao_nao_derruba() -> ResultadoFluxo:
    """Cenario 5: UN com fracao e recusada e a venda NAO cai."""
    nome = "Fluxo D - UN fracao recusada, venda nao cai (Cenario 5)"
    print(f"\n=== {nome} ===")

    bipar_no_leitor(f"2,5*{PRODUTO_UN_CODIGO}", "2,5 un de produto UN (fracao invalida)")
    # (a) status mostra a recusa; (b) o TOTAL continua R$ 0,00 (nada entrou).
    esperar_texto_na_regiao(REGIAO_STATUS, STATUS_UN_FRACAO, "D1 status fracao recusada")
    confirmar_total_estavel(TOTAL_ZERO, "D1 total segue 0,00 (nada entrou)")

    bipar_no_leitor(f"6*{PRODUTO_UN_CODIGO}", "6 unidades apos o erro")
    # A venda seguia viva: agora o TOTAL vira R$ 30,00 (o erro nao a derrubou).
    esperar_total_na_regiao(TOTAL_30, "D2 total 30,00 (venda seguia viva)")
    return ResultadoFluxo(
        nome, True, "fracao recusada (status + TOTAL 0,00); depois 6x => R$ 30,00"
    )


# ===========================================================================
# ORQUESTRACAO
# ===========================================================================

def preparar_ambiente_e_entrar_na_venda() -> None:
    """Confere resolucao, loga como operador e abre a tela de venda."""
    conferir_resolucao()
    print(
        "\nPreparando: assumindo app no ar (mvn javafx:run), Docker db_scopel "
        "de pe, janela MAXIMIZADA, seeds presentes (seed_g2.sql) e o caixa de "
        "caixa1 ABERTO (ver README)."
    )
    esperar(2.0, "dar tempo de focar a janela do app antes de digitar")
    logar(OPERADOR_LOGIN, OPERADOR_SENHA)
    if not CAIXA_JA_ABERTO:
        abortar(
            "preparacao",
            "CAIXA_JA_ABERTO=False nao e suportado por este script enxuto. "
            "Deixe o caixa de caixa1 ABERTO antes de rodar (ver README).",
        )
    ir_para_tela_de_venda()


FLUXOS_TOTAIS = 4


def main() -> int:
    """Roda os quatro fluxos em sequencia e imprime um resumo claro."""
    # FAILSAFE: jogar o mouse para o canto superior-esquerdo ABORTA tudo (escape
    # de emergencia, caso a automacao saia do controle).
    pyautogui.FAILSAFE = True
    pyautogui.PAUSE = 0.1  # pequena folga padrao entre chamadas do pyautogui

    resultados: list[ResultadoFluxo] = []
    try:
        preparar_ambiente_e_entrar_na_venda()

        resultados.append(fluxo_a_un_multiplicador())
        reiniciar_venda_pelo_menu()

        resultados.append(fluxo_b_kg_peso())
        reiniciar_venda_pelo_menu()

        resultados.append(fluxo_c_somatorio_rn07())
        reiniciar_venda_pelo_menu()

        resultados.append(fluxo_d_un_fracao_nao_derruba())

    except FalhaDeTeste as falha:
        print(f"\nTESTE ABORTADO: {falha}", file=sys.stderr)
        _imprimir_resumo(resultados, abortado_em=str(falha))
        return 1
    except pyautogui.FailSafeException:
        print(
            "\nTESTE INTERROMPIDO pelo FAILSAFE (mouse no canto).",
            file=sys.stderr,
        )
        _imprimir_resumo(resultados, abortado_em="failsafe acionado pelo usuario")
        return 130

    _imprimir_resumo(resultados, abortado_em=None)
    return 0


def _imprimir_resumo(resultados: list[ResultadoFluxo], abortado_em: str | None) -> None:
    """Imprime um resumo legivel de quais fluxos passaram."""
    print("\n" + "=" * 70)
    print("RESUMO G2 - Quantidade / multiplicador e peso na venda")
    print("=" * 70)
    for resultado in resultados:
        marca = "OK  " if resultado.passou else "FALHOU"
        print(f"  [{marca}] {resultado.nome}: {resultado.detalhe}")
    if abortado_em:
        print(f"  [PARADO] antes de concluir: {abortado_em}")
    nao_executados = FLUXOS_TOTAIS - len(resultados)
    if nao_executados > 0 and abortado_em:
        print(f"  [--] {nao_executados} fluxo(s) nao chegaram a rodar.")
    print("=" * 70)


if __name__ == "__main__":
    sys.exit(main())
