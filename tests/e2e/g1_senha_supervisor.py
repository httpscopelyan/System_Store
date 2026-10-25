"""
Automacao E2E do G1 "Autorizacao por senha de supervisor" (pyautogui).

Roteiro de origem: tests/e2e/roteiros/G1_senha_supervisor.md (parecer do John).

ESCOPO DELIBERADAMENTE PEQUENO (pyautogui e fragil por natureza):
  Fluxo A  -> Cenario 1  : cancelar item com gerente CORRETO (caminho feliz).
  Fluxo B  -> Cenario 4  : operador caixa2 (nao-gerente) e BARRADO.
  Fluxo C  -> Cenario 6b : desconto acima do teto + Esc NAO aplica.

Principios:
  - Teclado primeiro (Tab / Enter / Esc / digitar). Clique por pixel so em
    ultimo caso (aqui nao usamos nenhum).
  - Verificacao e SEMPRE pela TELA (OCR de regioes fixas). NUNCA le o banco.
  - Esperas EXPLICITAS entre passos, cada uma com o porque no comentario.
  - Qualquer falha de localizacao/verificacao ABORTA com mensagem dizendo
    exatamente qual passo quebrou.

ISTO E FERRAMENTA DE TESTE, NAO CODIGO DE PRODUCAO.
Este script NAO instala nada, NAO sobe o app e NAO mexe no banco. Quem prepara
o ambiente e roda e o DEV (abrir o app desktop e acao de ambiente). Veja o
README.md ao lado para pre-condicoes.
"""

from __future__ import annotations

import sys
import time
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
    from PIL import Image  # noqa: F401  (usado indiretamente pelo pyautogui.screenshot)

    _OCR_DISPONIVEL = True
except ImportError:  # pragma: no cover - ambiente do dev
    _OCR_DISPONIVEL = False


# ===========================================================================
# CONFIGURACAO (ajuste aqui, nao espalhe "numeros magicos" pelo codigo)
# ===========================================================================

# Resolucao em que as regioes de OCR abaixo foram calibradas. Se a maquina do
# dev usar outra resolucao/escala, recalibre REGIOES_* e rode de novo.
RESOLUCAO_ESPERADA = (1920, 1080)
# Se a resolucao real diferir, aborta (True) ou so avisa e segue (False).
EXIGIR_RESOLUCAO_EXATA = True

# --- Credenciais e dados de SEED (os mesmos do roteiro; NAO sao senhas de
# producao). Criados pelo caminho correto: usuarios via UserService/BCrypt. ---
OPERADOR_LOGIN = "caixa1"
OPERADOR_SENHA = "1234"

GERENTE_LOGIN = "gerente"
GERENTE_SENHA = "gerente123"

OPERADOR_EXTRA_LOGIN = "caixa2"  # OPERADOR, usado para provar o "nao e gerente"
OPERADOR_EXTRA_SENHA = "1234"

PRODUTO_CODIGO = "7891000100103"  # Arroz Tipo 1 5kg, R$ 25,00 (String!)
PRODUTO_PRECO_UNIT = "25,00"

# Desconto em REAIS (nao e %). 20,00 sobre subtotal 100,00 = 20% > teto de 10%.
DESCONTO_ACIMA_DO_TETO = "20"

# O caixa de caixa1 ja deve estar ABERTO antes de rodar (deixe o banco nesse
# estado). Abrir caixa pela UI e fragil e varia de tela; preferimos pre-abrir.
CAIXA_JA_ABERTO = True

# --- Navegacao por teclado --------------------------------------------------
# Atalho para ir a tela de Venda (menu F2 / botao Vender, conforme roteiro).
ATALHO_TELA_VENDA = "f2"

# ATENCAO: a ordem do Tab segue a ordem dos nos no sale.fxml:
#   codeField -> itemsTable -> discountField -> [Aplicar desconto] ->
#   customerIdField -> [Vincular cliente] -> cancelItemCodeField ->
#   [Cancelar item] -> cancelReasonField -> [Cancelar venda] -> ...
# Partindo SEMPRE do codeField (que mantem o foco apos bipar, RNF03).
# Se a TableView capturar/soltar o foco de forma diferente na maquina do dev,
# ajuste estes contadores (sao a parte mais fragil da navegacao por teclado).
TABS_CODE_ATE_DESCONTO = 2           # codeField -> itemsTable -> discountField
TABS_DESCONTO_ATE_BOTAO_DESCONTO = 1  # discountField -> [Aplicar desconto]
TABS_CODE_ATE_CANCELAR_ITEM_CAMPO = 6  # codeField -> ... -> cancelItemCodeField
TABS_CAMPO_ATE_BOTAO_CANCELAR_ITEM = 1  # cancelItemCodeField -> [Cancelar item]

# Ordem de campos do diferentes diálogos ------------------------------------
# Login: campo usuario (ja focado) -> Tab -> senha -> Enter.
# Dialogo de gerente: campo Login (ja focado) -> Tab -> Senha -> Enter.
TABS_ENTRE_LOGIN_E_SENHA = 1

# --- Esperas EXPLICITAS (segundos). Cada uma documenta o porque. -----------
ESPERA_APOS_TECLA = 0.25        # deixa o JavaFX processar o evento de tecla
ESPERA_ABRIR_TELA = 2.0         # troca de tela/scene do shell nao e instantanea
ESPERA_ABRIR_DIALOGO = 1.2      # dialogo modal leva um tempo para montar
ESPERA_FECHAR_DIALOGO = 1.0     # fechar modal + repintar a tela de venda
ESPERA_APOS_BIPAR = 0.6         # service grava item + recalcula total + cupom
ESPERA_LOGIN = 3.0              # autenticar + montar o shell principal

# --- Timeouts e poll para as verificacoes por OCR --------------------------
TIMEOUT_VERIFICACAO = 8.0       # tempo maximo esperando um texto aparecer
INTERVALO_POLL = 0.5            # frequencia de releitura da regiao

# --- Regioes de tela (left, top, width, height) calibradas para 1920x1080,
# janela MAXIMIZADA, tema/CSS padrao. Recalibre na maquina do dev. ----------
# Dica para calibrar: pyautogui.position() mostra o cursor; capture um
# screenshot e meca o retangulo que envolve cada elemento.
REGIAO_TOTAL = (1540, 250, 320, 90)          # rotulo gigante "R$ x,xx"
REGIAO_STATUS = (24, 560, 900, 40)           # statusLabel abaixo da tabela
REGIAO_TABELA_ITENS = (24, 300, 1100, 250)   # area da TableView (linhas)
REGIAO_TITULO_DIALOGO = (640, 360, 640, 60)  # topo do modal de gerente
REGIAO_ERRO_DIALOGO = (640, 480, 640, 50)    # linha de erro vermelha do modal

# Textos esperados na tela (comparacao e case-insensitive + normaliza espacos).
TOTAL_ZERO = "R$ 0,00"
TOTAL_UMA_UNIDADE = "R$ 25,00"
TOTAL_TRES_UNIDADES = "R$ 75,00"
TOTAL_QUATRO_UNIDADES = "R$ 100,00"
TEXTO_PRODUTO_NA_TABELA = "Arroz"
TITULO_DIALOGO_GERENTE = "Autorizacao de gerente"   # casa com "Autorizacao..."
STATUS_ITEM_CANCELADO = "Item cancelado"
ERRO_NAO_E_GERENTE = "nao e gerente"                # casa "Usuario nao e gerente"
STATUS_DESCONTO_NAO_AUTORIZADO = "nao autorizado"


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


def digitar_texto(texto: str) -> None:
    """Digita texto no campo em foco; 'interval' evita perder caracteres."""
    pyautogui.typewrite(texto, interval=0.04)
    time.sleep(ESPERA_APOS_TECLA)


def pressionar(tecla: str, vezes: int = 1) -> None:
    """Pressiona uma tecla N vezes, com micro-pausa para o JavaFX acompanhar."""
    for _ in range(vezes):
        pyautogui.press(tecla)
        time.sleep(ESPERA_APOS_TECLA)


def navegar_com_tab(quantidade_de_tabs: int) -> None:
    """Anda 'quantidade_de_tabs' campos/botoes adiante com Tab."""
    pressionar("tab", vezes=quantidade_de_tabs)


def _normalizar(texto: str) -> str:
    """Minusculas + espacos colapsados, para comparacao tolerante do OCR."""
    return " ".join(texto.lower().split())


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


def esperar_texto_na_regiao(
    regiao: tuple[int, int, int, int],
    esperado: str,
    passo: str,
    timeout: float = TIMEOUT_VERIFICACAO,
) -> None:
    """Faz poll da regiao ate 'esperado' aparecer; senao, aborta no 'passo'."""
    alvo = _normalizar(esperado)
    prazo = time.time() + timeout
    ultimo_lido = ""
    while time.time() < prazo:
        ultimo_lido = _normalizar(ler_texto_da_regiao(regiao))
        if alvo in ultimo_lido:
            print(f"    OK [{passo}] encontrou '{esperado}'")
            return
        time.sleep(INTERVALO_POLL)
    abortar(
        passo,
        f"esperava ver '{esperado}' na regiao {regiao}, "
        f"mas o OCR leu '{ultimo_lido}' ate o timeout de {timeout:.0f}s.",
    )


def esperar_texto_ausente_na_regiao(
    regiao: tuple[int, int, int, int],
    nao_esperado: str,
    passo: str,
    timeout: float = TIMEOUT_VERIFICACAO,
) -> None:
    """Faz poll ate 'nao_esperado' sumir da regiao; senao, aborta no 'passo'."""
    alvo = _normalizar(nao_esperado)
    prazo = time.time() + timeout
    ultimo_lido = ""
    while time.time() < prazo:
        ultimo_lido = _normalizar(ler_texto_da_regiao(regiao))
        if alvo not in ultimo_lido:
            print(f"    OK [{passo}] '{nao_esperado}' nao esta mais presente")
            return
        time.sleep(INTERVALO_POLL)
    abortar(
        passo,
        f"esperava que '{nao_esperado}' sumisse da regiao {regiao}, "
        f"mas o OCR ainda leu '{ultimo_lido}' apos {timeout:.0f}s.",
    )


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
# ACOES DE ALTO NIVEL (login, abrir venda, bipar, diálogo de gerente)
# ===========================================================================

def logar(login: str, senha: str) -> None:
    """Faz login na tela inicial (campo usuario ja focado -> Tab -> senha -> Enter)."""
    print(f"  Login como '{login}'")
    digitar_texto(login)
    navegar_com_tab(TABS_ENTRE_LOGIN_E_SENHA)
    digitar_texto(senha)
    pressionar("enter")
    esperar(ESPERA_LOGIN, "autenticar e montar o shell principal")


def ir_para_tela_de_venda() -> None:
    """Navega para a tela de Venda e confere pelo rotulo de TOTAL."""
    print("  Indo para a tela de Venda")
    pressionar(ATALHO_TELA_VENDA)
    esperar(ESPERA_ABRIR_TELA, "trocar o centro do shell para a tela de venda")
    # A tela de venda sempre mostra o rotulo TOTAL; serve de ancora de que chegou.
    esperar_texto_na_regiao(REGIAO_TOTAL, "R$", "abrir tela de venda")


def bipar_produto(vezes: int) -> None:
    """Bipa o produto de seed 'vezes' (digita o codigo + Enter a cada vez)."""
    print(f"  Bipando o produto {PRODUTO_CODIGO} x{vezes}")
    for indice in range(vezes):
        digitar_texto(PRODUTO_CODIGO)
        pressionar("enter")
        esperar(ESPERA_APOS_BIPAR, f"gravar item #{indice + 1} e recalcular total")


def preencher_dialogo_de_gerente_e_autorizar(login: str, senha: str) -> None:
    """No modal de gerente (Login ja focado): Login -> Tab -> Senha -> Enter."""
    print(f"  Autorizando no dialogo de gerente como '{login}'")
    digitar_texto(login)
    navegar_com_tab(TABS_ENTRE_LOGIN_E_SENHA)
    digitar_texto(senha)
    pressionar("enter")


def abrir_dialogo_cancelar_item() -> None:
    """Preenche o codigo em 'cancelar item' e aciona o botao (abre o modal)."""
    print("  Acionando 'Cancelar item' (deve abrir o dialogo de gerente)")
    # Partimos do codeField, que mantem o foco apos bipar (RNF03).
    navegar_com_tab(TABS_CODE_ATE_CANCELAR_ITEM_CAMPO)
    digitar_texto(PRODUTO_CODIGO)
    navegar_com_tab(TABS_CAMPO_ATE_BOTAO_CANCELAR_ITEM)
    pressionar("enter")  # aciona o botao em foco
    esperar(ESPERA_ABRIR_DIALOGO, "montar o modal de autorizacao de gerente")


def abrir_dialogo_desconto_acima_do_teto() -> None:
    """Preenche desconto acima do teto e aciona o botao (abre o modal)."""
    print("  Acionando 'Aplicar desconto' acima do teto (deve abrir o dialogo)")
    navegar_com_tab(TABS_CODE_ATE_DESCONTO)
    digitar_texto(DESCONTO_ACIMA_DO_TETO)
    navegar_com_tab(TABS_DESCONTO_ATE_BOTAO_DESCONTO)
    pressionar("enter")  # aciona o botao em foco
    esperar(ESPERA_ABRIR_DIALOGO, "montar o modal de autorizacao de gerente")


def garantir_foco_no_codigo_de_barras() -> None:
    """Recoloca o foco no campo de codigo de barras antes de navegar por Tab.

    Depois de fechar um modal o foco pode cair em outro no. Em vez de clicar por
    pixel, usamos o atalho da tela de venda para reposicionar de forma previsivel.
    """
    pressionar(ATALHO_TELA_VENDA)
    esperar(ESPERA_APOS_TECLA, "reassentar o foco na tela de venda")


# ===========================================================================
# FLUXOS COBERTOS (um metodo por fluxo, verificacoes embutidas)
# ===========================================================================

def fluxo_a_cancelar_item_com_gerente() -> ResultadoFluxo:
    """Cenario 1: cancelar item com senha de gerente CORRETA (caminho feliz)."""
    nome = "Fluxo A - cancelar item com gerente (Cenario 1)"
    print(f"\n=== {nome} ===")

    bipar_produto(3)
    # Verificacao: 3 arroz somam na mesma linha e o TOTAL fica R$ 75,00.
    esperar_texto_na_regiao(
        REGIAO_TABELA_ITENS, TEXTO_PRODUTO_NA_TABELA, "A3 linha do produto"
    )
    esperar_texto_na_regiao(REGIAO_TOTAL, TOTAL_TRES_UNIDADES, "A3 total 75,00")

    abrir_dialogo_cancelar_item()
    # Verificacao: o modal de gerente esta na tela.
    esperar_texto_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "A4 dialogo aberto"
    )

    preencher_dialogo_de_gerente_e_autorizar(GERENTE_LOGIN, GERENTE_SENHA)
    esperar(ESPERA_FECHAR_DIALOGO, "fechar o modal e repintar a venda")

    # Verificacao tripla (roteiro passo 5): dialogo fechou, tabela vazia, total 0,
    # status "Item cancelado".
    esperar_texto_ausente_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "A5 dialogo fechou"
    )
    esperar_texto_ausente_na_regiao(
        REGIAO_TABELA_ITENS, TEXTO_PRODUTO_NA_TABELA, "A5 tabela esvaziou"
    )
    esperar_texto_na_regiao(REGIAO_TOTAL, TOTAL_ZERO, "A5 total 0,00")
    esperar_texto_na_regiao(
        REGIAO_STATUS, STATUS_ITEM_CANCELADO, "A5 status item cancelado"
    )
    return ResultadoFluxo(nome, True, "item cancelado com gerente; TOTAL R$ 0,00")


def fluxo_b_operador_nao_e_gerente() -> ResultadoFluxo:
    """Cenario 4: operador caixa2 tenta autorizar e e BARRADO."""
    nome = "Fluxo B - operador nao-gerente barrado (Cenario 4)"
    print(f"\n=== {nome} ===")

    bipar_produto(1)
    esperar_texto_na_regiao(REGIAO_TOTAL, TOTAL_UMA_UNIDADE, "B1 total 25,00")

    abrir_dialogo_cancelar_item()
    esperar_texto_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "B2 dialogo aberto"
    )

    # Credencial de OPERADOR (valida, porem sem papel de gerente).
    preencher_dialogo_de_gerente_e_autorizar(
        OPERADOR_EXTRA_LOGIN, OPERADOR_EXTRA_SENHA
    )
    esperar(ESPERA_APOS_TECLA, "processar a rejeicao e exibir o erro inline")

    # Verificacao: erro "Usuario nao e gerente" E o dialogo CONTINUA aberto.
    esperar_texto_na_regiao(
        REGIAO_ERRO_DIALOGO, ERRO_NAO_E_GERENTE, "B2 erro nao e gerente"
    )
    esperar_texto_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "B2 dialogo segue aberto"
    )

    pressionar("escape")  # desistir
    esperar(ESPERA_FECHAR_DIALOGO, "fechar o modal apos o Esc")

    # Verificacao: diálogo fechou e NADA foi cancelado (arroz segue, TOTAL 25,00).
    esperar_texto_ausente_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "B3 dialogo fechou"
    )
    esperar_texto_na_regiao(
        REGIAO_TABELA_ITENS, TEXTO_PRODUTO_NA_TABELA, "B3 item ainda presente"
    )
    esperar_texto_na_regiao(REGIAO_TOTAL, TOTAL_UMA_UNIDADE, "B3 total 25,00 intacto")
    return ResultadoFluxo(
        nome, True, "operador barrado ('nao e gerente'); nada cancelado"
    )


def fluxo_c_desconto_acima_do_teto_esc() -> ResultadoFluxo:
    """Cenario 6b: desconto acima do teto + Esc NAO aplica."""
    nome = "Fluxo C - desconto acima do teto, Esc nao aplica (Cenario 6b)"
    print(f"\n=== {nome} ===")

    bipar_produto(4)
    esperar_texto_na_regiao(REGIAO_TOTAL, TOTAL_QUATRO_UNIDADES, "C1 subtotal 100,00")

    abrir_dialogo_desconto_acima_do_teto()
    # Verificacao: desconto > teto de 10% abre o modal de gerente.
    esperar_texto_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "C2 dialogo aberto"
    )

    pressionar("escape")  # desistir sem autorizar
    esperar(ESPERA_FECHAR_DIALOGO, "fechar o modal apos o Esc")

    # Verificacao: diálogo fechou e o desconto NAO foi aplicado (TOTAL 100,00).
    esperar_texto_ausente_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "C3 dialogo fechou"
    )
    esperar_texto_na_regiao(
        REGIAO_TOTAL, TOTAL_QUATRO_UNIDADES, "C3 total intacto 100,00"
    )
    esperar_texto_na_regiao(
        REGIAO_STATUS, STATUS_DESCONTO_NAO_AUTORIZADO, "C3 status nao autorizado"
    )
    return ResultadoFluxo(
        nome, True, "desconto acima do teto nao aplicado apos Esc; TOTAL R$ 100,00"
    )


# ===========================================================================
# ORQUESTRACAO
# ===========================================================================

def preparar_ambiente_e_entrar_na_venda() -> None:
    """Confere resolucao, loga como operador e abre a tela de venda."""
    conferir_resolucao()
    print(
        "\nPreparando: assumindo app no ar (mvn javafx:run), Docker db_scopel "
        "de pe, janela MAXIMIZADA e seeds presentes (ver README)."
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


def recomecar_venda_limpa_com_gerente() -> None:
    """Entre fluxos, zera a venda cancelando-a com gerente, se houver itens.

    Mantem cada fluxo independente sem deixar residuo de itens de um fluxo
    anterior. Se o TOTAL ja esta R$ 0,00, nao faz nada.
    """
    garantir_foco_no_codigo_de_barras()
    texto_total = _normalizar(ler_texto_da_regiao(REGIAO_TOTAL))
    if _normalizar(TOTAL_ZERO) in texto_total:
        print("  Venda ja esta vazia; nada a limpar.")
        return
    print("  Limpando a venda anterior (cancelar venda com gerente)")
    # cancelReasonField fica logo apos cancelItemCodeField + botao 'Cancelar item'.
    navegar_com_tab(TABS_CODE_ATE_CANCELAR_ITEM_CAMPO + 2)  # -> cancelReasonField
    digitar_texto("reset automatizado e2e")
    navegar_com_tab(1)  # -> botao 'Cancelar venda'
    pressionar("enter")
    esperar(ESPERA_ABRIR_DIALOGO, "montar o modal de autorizacao de gerente")
    esperar_texto_na_regiao(
        REGIAO_TITULO_DIALOGO, TITULO_DIALOGO_GERENTE, "reset dialogo aberto"
    )
    preencher_dialogo_de_gerente_e_autorizar(GERENTE_LOGIN, GERENTE_SENHA)
    esperar(ESPERA_FECHAR_DIALOGO, "fechar o modal e abrir nova venda vazia")
    esperar_texto_na_regiao(REGIAO_TOTAL, TOTAL_ZERO, "reset total 0,00")


def main() -> int:
    """Roda os tres fluxos em sequencia e imprime um resumo claro."""
    # FAILSAFE: jogar o mouse para o canto superior-esquerdo ABORTA tudo (escape
    # de emergencia, caso a automacao saia do controle).
    pyautogui.FAILSAFE = True
    pyautogui.PAUSE = 0.1  # pequena folga padrao entre chamadas do pyautogui

    resultados: list[ResultadoFluxo] = []
    try:
        preparar_ambiente_e_entrar_na_venda()

        resultados.append(fluxo_a_cancelar_item_com_gerente())
        recomecar_venda_limpa_com_gerente()

        resultados.append(fluxo_b_operador_nao_e_gerente())
        recomecar_venda_limpa_com_gerente()

        resultados.append(fluxo_c_desconto_acima_do_teto_esc())
        recomecar_venda_limpa_com_gerente()

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
    print("RESUMO G1 - Autorizacao por senha de supervisor")
    print("=" * 70)
    for resultado in resultados:
        marca = "OK  " if resultado.passou else "FALHOU"
        print(f"  [{marca}] {resultado.nome}: {resultado.detalhe}")
    if abortado_em:
        print(f"  [PARADO] antes de concluir: {abortado_em}")
    nao_executados = 3 - len(resultados)
    if nao_executados > 0 and abortado_em:
        print(f"  [--] {nao_executados} fluxo(s) nao chegaram a rodar.")
    print("=" * 70)


if __name__ == "__main__":
    sys.exit(main())
