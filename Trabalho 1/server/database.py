import sqlite3

def init_db():
    conn = sqlite3.connect('questions.db')
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS questions
                (id INTEGER PRIMARY KEY,
                question TEXT,
                options TEXT,
                correct_index INTEGER)''')
    conn.commit()
    conn.close()

def insert_sample_questions():
    questions = [
        ("Qual é a capital da Itália?", "Roma;Paris;Lisboa;Londres", 0),
        ("Quanto é 2 + 2?", "3;4;5;6", 1),
        ("Quem escreveu Dom Quixote?", "Machado de Assis;Cervantes;Shakespeare;Dante", 1),
        ("Qual é o maior planeta do Sistema Solar?", "Terra;Júpiter;Marte;Vênus", 1),
        ("Quem pintou a Mona Lisa?", "Vincent van Gogh;Pablo Picasso;Leonardo da Vinci;Claude Monet", 2),
        ("Qual é o maior oceano do mundo?", "Oceano Atlântico;Oceano Índico;Oceano Ártico;Oceano Pacífico", 3),
        ("Quantos elementos químicos a tabela periódica possui?", "118;92;150;103", 0),
        ("Qual é o país mais populoso do mundo?", "Índia;Estados Unidos;China;Brasil", 2),
        ("Qual é a capital do Brasil?", "Rio de Janeiro;São Paulo;Brasília;Belo Horizonte", 2),
        ("Qual é o símbolo químico do ouro?", "Ag;Au;Fe;Pb", 1),
        ("Qual é o maior deserto do mundo?", "Deserto do Saara;Deserto de Gobi;Deserto da Arábia;Deserto da Antártida", 3),
        ("Quem foi o primeiro presidente dos Estados Unidos?", "Thomas Jefferson;Abraham Lincoln;George Washington;John Adams", 2),
        ("Qual é o menor país do mundo?", "Mônaco;Vaticano;San Marino;Liechtenstein", 1),
        ("Qual é a fórmula química da água?", "CO2;H2O;NaCl;O2", 1),
        ("Qual é o animal mais rápido do mundo?", "Guepardo;Falcão-peregrino;Lebre;Peixe-vela", 1),
        ("Qual é a capital da França?", "Berlim;Roma;Paris;Madrid", 2),
        ("Quantos continentes existem no mundo?", "5;6;7;8", 2),
        ("Quem descobriu a penicilina?", "Marie Curie;Alexander Fleming;Louis Pasteur;Isaac Newton", 1),
        ("Qual é o maior rio do mundo em volume de água?", "Rio Amazonas;Rio Nilo;Rio Mississippi;Rio Yangtzé", 0),
        ("Qual é o planeta mais próximo do Sol?", "Vênus;Terra;Mercúrio;Marte", 2),
        ("Quem foi o autor de '1984'?", "George Orwell;Aldous Huxley;Ray Bradbury;Franz Kafka", 0),
        ("Qual é a capital do Japão?", "Pequim;Tóquio;Seul;Bangcoc", 1),
        ("Qual é o maior osso do corpo humano?", "Fêmur;Tíbia;Úmero;Crânio", 0),
        ("Quantos anos durou a Primeira Guerra Mundial?", "4 anos;5 anos;6 anos;7 anos", 0),
        ("Qual é o país com maior número de idiomas falados?", "Índia;Papua Nova Guiné;Indonésia;Nigéria", 1),
        ("Qual é o maior vulcão ativo do mundo?", "Monte Fuji;Monte Vesúvio;Mauna Loa;Monte Santa Helena", 2),
        ("Quem foi o primeiro homem a pisar na Lua?", "Neil Armstrong;Buzz Aldrin;Yuri Gagarin;Michael Collins", 0),
        ("Qual é o metal mais condutor de eletricidade?", "Ouro;Prata;Cobre;Alumínio", 1),
        ("Qual é o país com maior área territorial do mundo?", "Canadá;China;Estados Unidos;Rússia", 3),
        ("Qual é o nome do processo de divisão celular?", "Mitose;Meiose;Fotosíntese;Respiração", 0),
        ("Qual é o maior felino do mundo?", "Leão;Tigre;Onça-pintada;Leopardo", 1),
        ("Qual é o símbolo químico do sódio?", "So;Na;K;Mg", 1)
    ]
    conn = sqlite3.connect('questions.db')
    c = conn.cursor()
    c.execute("DELETE FROM questions")
    c.executemany("INSERT INTO questions (question, options, correct_index) VALUES (?, ?, ?)", questions)
    conn.commit()
    conn.close()

def get_questions():
    conn = sqlite3.connect('questions.db')
    c = conn.cursor()
    c.execute("SELECT question, options, correct_index FROM questions ORDER BY RANDOM() LIMIT 3")
    rows = c.fetchall()
    conn.close()
    return [{'question': row[0], 'options': row[1], 'correct_index': row[2]} for row in rows]