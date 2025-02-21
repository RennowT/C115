import socket
import sqlite3
from database import get_questions

HOST = '127.0.0.1'
PORT = 65432

def handle_client(conn):
    try:
        questions = get_questions()
        if len(questions) < 3:
            conn.close()
            return

        user_answers = []
        for q in questions:
            question_str = f"{q['question']};{q['options']}"
            conn.sendall(question_str.encode())
            client_answer = conn.recv(1024).decode().strip()
            user_answers.append(int(client_answer))

        results = []
        for i, q in enumerate(questions):
            results.append(1 if user_answers[i] == q['correct_index'] else 0)

        total = sum(results)
        response = f"{';'.join(map(str, results))};{total}"
        conn.sendall(response.encode())
    finally:
        conn.close()

def main():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind((HOST, PORT))
        s.listen()
        print("Servidor aguardando conexões...")
        while True:
            conn, addr = s.accept()
            print(f"Conectado por {addr}")
            handle_client(conn)

if __name__ == "__main__":
    main()