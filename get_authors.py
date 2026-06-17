import json, urllib.request

req = urllib.request.urlopen('https://api.github.com/repos/vgskye/e4mc-minecraft-architectury/pulls?state=all')
prs = json.loads(req.read())

print("Authors:")
for pr in prs:
    title = pr['title'].lower()
    if 'translat' in title or 'lang' in title or 'russian' in title or 'polish' in title:
        print(f"PR {pr['number']} ({pr['title']}): @{pr['user']['login']}")
