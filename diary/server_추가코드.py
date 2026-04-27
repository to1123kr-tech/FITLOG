# ── server.py에 이 코드 추가 ──
# (기존 import 아래에)
import os

# (기존 라우트들 아래에 추가)
DIARY_DIR = os.path.join(os.path.dirname(__file__), 'diary')

@app.route('/diary/')
@app.route('/diary/<path:filename>')
def serve_diary(filename='diary.html'):
    return send_from_directory(DIARY_DIR, filename)

# send_from_directory는 flask에서 이미 import 되어있을 거예요.
# 없으면 상단에: from flask import send_from_directory
