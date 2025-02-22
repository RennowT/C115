# Trabalho 1 - Sistema de Quiz Cliente-Servidor

## Proposta

O objetivo deste projeto é criar um sistema cliente-servidor em Python, onde o servidor envia três questões de múltipla escolha para o cliente. O cliente responde às questões, e o servidor retorna o número de acertos, além de uma lista detalhada indicando quais questões foram acertadas ou erradas.

**Exemplo de questão:**

```
Qual é a capital da Itália?

1. Roma
2. Paris
3. Lisboa
4. Londres
```

---

## Estrutura de Projeto

O projeto está organizado da seguinte forma:

```
.
├── server
│   ├── server.py            # Código do servidor
│   ├── database.py          # Funções de acesso ao banco de dados
│   └── questions.db         # Banco de dados SQLite (gerado automaticamente)
└── client
    └── client.py            # Código do cliente
```

---

## Requisitos

- Python 3.x
- Biblioteca `sqlite3` (já incluída na biblioteca padrão do Python)

---

## Instruções de Uso

#### 1. Preparar o Ambiente

Clone o repositório do projeto e navegue até a pasta raiz:

```bash
git clone https://github.com/RennowT/C115
cd '.\C115\Trabalho 1'
```

#### 2. Configurar o Banco de Dados

Navegue até a pasta do servidor e execute o script para inicializar o banco de dados e inserir as questões de exemplo:

```bash
cd server
python3
```

No interpretador Python, execute os seguintes comandos:

```bash
from database import init_db, insert_sample_questions
init_db()  # Cria o banco de dados e a tabela de questões
insert_sample_questions()  # Insere questões de exemplo no banco de dados
exit()  # Sai do interpretador Python
```

#### 3. Iniciar o Servidor

Na pasta `server`, execute o servidor:

```bash
python3 server.py
```

O servidor estará aguardando conexões na porta `65432`.

#### 4. Executar o Cliente

Em outro terminal, navegue até a pasta `client` e execute o cliente:

```bash
cd ../client
python3 client.py
```

O cliente se conectará ao servidor e começará a receber as questões.

---

## Funcionamento do Sistema

1. **Servidor:**
    - Aguarda conexões de clientes.
    - Seleciona 3 questões aleatórias do banco de dados.
    - Envia cada questão ao cliente, uma por vez.
    - Recebe as respostas do cliente.
    - Calcula o número de acertos e envia o resultado de volta ao cliente.

2. **Cliente:**
    - Conecta-se ao servidor.
    - Recebe as questões e exibe-as para o usuário.
    - Coleta as respostas do usuário e as envia ao servidor.
    - Exibe o resultado final, indicando quais questões foram acertadas ou erradas.

---

## Exemplo de Interação

#### Cliente:

```
Conectado ao servidor. Responda as questões:

Qual é a capital da Itália?
0. Roma
1. Paris
2. Lisboa
3. Londres
Sua resposta (0-3): 0

Quanto é 2 + 2?
0. 3
1. 4
2. 5
3. 6
Sua resposta (0-3): 1

Quem escreveu Dom Quixote?
0. Machado de Assis
1. Cervantes
2. Shakespeare
3. Dante
Sua resposta (0-3): 1

Resultado: 2/3 acertos
Questão 1: ✅ Acerto
Questão 2: ✅ Acerto
Questão 3: ❌ Erro
```

---

## Detalhes Técnicos

#### Banco de Dados (SQLite)

- Tabela `questions`:
    - `id`: Identificador único da questão.
    - `question`: Texto da pergunta.
    - `options`: Opções de resposta, separadas por `;`.
    - `correct_index`: Índice da resposta correta (0 a 3).

#### Comunicação Cliente-Servidor

- O servidor e o cliente se comunicam via sockets TCP.
- As questões e respostas são enviadas como strings codificadas em UTF-8.
- O resultado final é enviado como uma string no formato `acerto1;acerto2;acerto3;total`.