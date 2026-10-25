#!/usr/bin/env python3
"""Render doc/stack-test-guide.pdf: how to start the local stack, the URLs, the test accounts and a walk-through
that exercises every service. Re-run after changing scripts/start-stack.sh or the accounts:

    python3 scripts/stack-test-guide.py [doc/stack-test-guide.pdf]
"""
import sys
from datetime import date

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (KeepTogether, ListFlowable, ListItem, PageBreak, Paragraph, Preformatted,
                                SimpleDocTemplate, Spacer, Table, TableStyle)

OUT = sys.argv[1] if len(sys.argv) > 1 else 'doc/stack-test-guide.pdf'
TODAY = date.today().isoformat()

ss = getSampleStyleSheet()
H1 = ParagraphStyle('H1', parent=ss['Heading1'], fontSize=17, spaceAfter=8)
H2 = ParagraphStyle('H2', parent=ss['Heading2'], fontSize=12.5, spaceBefore=12, spaceAfter=4)
H3 = ParagraphStyle('H3', parent=ss['Heading3'], fontSize=10.5, spaceBefore=8, spaceAfter=2)
B = ParagraphStyle('B', parent=ss['BodyText'], fontSize=9.5, leading=13)
SM = ParagraphStyle('SM', parent=B, fontSize=8.5, leading=11)
NOTE = ParagraphStyle('NOTE', parent=SM, textColor=colors.HexColor('#555555'), leftIndent=8)
CODE = ParagraphStyle('CODE', parent=B, fontName='Courier', fontSize=7.8, leading=10, backColor=colors.whitesmoke,
                      leftIndent=4, borderPadding=3, spaceBefore=3, spaceAfter=6)


def c(t):
    return '<font face="Courier">%s</font>' % t


def p(t, s=B):
    return Paragraph(t, s)


def bullets(items, s=B):
    return ListFlowable([ListItem(p(i, s), leftIndent=12) for i in items], bulletType='bullet', start='•',
                        leftIndent=12, bulletFontSize=8)


def steps(items, s=B):
    return ListFlowable([ListItem(p(i, s), leftIndent=14) for i in items], bulletType='1', leftIndent=14,
                        bulletFontSize=8.5)


def table(rows, widths):
    t = Table([[p(x, SM) for x in r] for r in rows], colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#dde4ee')),
                           ('GRID', (0, 0), (-1, -1), 0.4, colors.grey), ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                           ('LEFTPADDING', (0, 0), (-1, -1), 4), ('RIGHTPADDING', (0, 0), (-1, -1), 4)]))
    return t


def code(t):
    return Preformatted(t, CODE)


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.grey)
    canvas.drawString(2 * cm, 1.2 * cm, 'announce-reactive · local stack test guide · %s' % TODAY)
    canvas.drawRightString(A4[0] - 2 * cm, 1.2 * cm, 'page %d' % doc.page)
    canvas.restoreState()


doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=2 * cm, rightMargin=2 * cm, topMargin=2 * cm, bottomMargin=2 * cm,
                        title='announce-reactive: local stack test guide', author='Claude Code session')
s = []

s.append(p('announce-reactive: local stack test guide', H1))
s.append(p('How to start the whole stack on the development machine, where to reach each part, which member and staff '
           'accounts to use, and a walk-through that exercises every service once. Rendered %s from %s; the accounts and '
           'ports below are the ones %s prints when it is done.' % (TODAY, c('scripts/stack-test-guide.py'),
                                                                    c('scripts/start-stack.sh'))))

# ------------------------------------------------------------------ 1
s.append(p('1. Start and stop', H2))
s.append(p('From the repository root (a worktree is fine, the script builds from the checkout it lives in):'))
s.append(code('scripts/start-stack.sh                 # builds what is missing on the first run, then starts everything\n'
              'scripts/start-stack.sh --build         # rebuild every jar first (after pulling code)\n'
              'scripts/start-stack.sh --without-order # skip the order module (auctions, orders, payments, shipping)\n'
              'scripts/start-stack.sh --search=noop   # do not index into OCS; search stays as it is\n'
              'scripts/stop-stack.sh                  # stop what start-stack.sh started (pid files)\n'
              'scripts/stop-stack.sh --all            # also stop announce services started by hand\n'
              'scripts/stop-stack.sh --all --infra    # and stop the docker/cqrs containers'))
s.append(p('What the script does, in order:'))
s.append(steps([
    'Refuses to start if a service of a previous run is alive or one of the ports is taken, and names the process.',
    'Checks MySQL %s on localhost:3306 (user %s). The database is built by %s and is never the %s database.'
    % (c('announce_reactive_db'), c('announce'), c('db-reactive/sql/build_announce_reactive_db.sh'), c('litemall')),
    'Checks the six %s containers (EventSourcingDB, Kafka, Elasticsearch, OCS indexer, searcher, suggest) and runs '
    '%s when one is missing, then waits for the health checks.' % (c('docker/cqrs'), c('docker compose up -d')),
    'Builds the backends in one Maven reactor (%s, so db-reactive, cqrs-reactive and the api modules come from this '
    'checkout, not from a stale SNAPSHOT in %s) and the two edges with their SPAs (%s profile, npm). Build logs: '
    '%s.' % (c('-am'), c('~/.m2'), c('webapp'), c('~/.announce-stack/logs/build-*.log')),
    'Starts announce-management, order, messaging, the customer edge and the admin edge with Eureka, the config server '
    'and discovery switched off; the edges get the backend URLs as %s overrides. messaging has no bootable jar and runs '
    'under %s.' % (c('--announce.routes.*'), c('mvn spring-boot:run')),
    'Waits until each service answers over HTTP and prints the URLs and accounts.',
]))
s.append(p('Java 21 is taken from %s unless %s is set. Logs and pid files live in %s (override with %s).'
           % (c('/usr/lib/jvm/java-21-openjdk-amd64'), c('JAVA_HOME'), c('~/.announce-stack'), c('STACK_HOME')), NOTE))

# ------------------------------------------------------------------ 2
s.append(p('2. Addresses', H2))
s.append(table([
    ['What', 'URL', 'Notes'],
    ['Customer SPA and edge', c('http://localhost:8090/'),
     'The marketplace as members see it. Port 8080 is usually taken on this machine, hence 8090 (%s).' % c('PORT_CUSTOMER')],
    ['Admin SPA and edge', c('http://localhost:8091/'), 'Moderation, catalogue, members, trade lists, support queue (%s).' % c('PORT_ADMIN')],
    ['announce-management', c('http://localhost:8085/'),
     'Announces, categories, brands, crawled listings, search indexing. Direct calls answer 401 without %s: go through an edge.' % c('X-Service-Token')],
    ['order', c('http://localhost:8083/'), 'Auctions, orders, payments (fake provider), shipping (fake carrier). Same 401 rule.'],
    ['messaging', c('http://localhost:8086/'), 'Conversations and WebSockets. Same 401 rule.'],
    ['EventSourcingDB UI', c('http://localhost:3000/'), 'Token %s. The event store behind every aggregate.' % c('secret')],
    ['Elasticsearch', c('http://localhost:9200/'), 'Index %s.' % c('announce_index')],
    ['OCS searcher / indexer / suggest', c('localhost:8534 / 8535 / 8536'), 'The search tier the SPA queries through the customer edge.'],
    ['Kafka', c('localhost:9094'), 'Topics %s, %s, %s, %s, %s.' % (c('announce.v1'), c('auction.v1'), c('order.v1'), c('messaging.v1'), c('messaging.live'))],
    ['Health', c('/actuator/health') + ' on 8085, 8083, 8086',
     'Reports DOWN locally because the Redis and Kafka-binder indicators cannot reach their hosts. The services work; the script treats 503 as up.'],
], [3.4 * cm, 5.2 * cm, 8.4 * cm]))

# ------------------------------------------------------------------ 3
s.append(p('3. Accounts', H2))
s.append(p('3.1 Members (customer SPA, %s)' % c('http://localhost:8090/login'), H3))
s.append(table([
    ['Username', 'Password', 'Id', 'What it is good for'],
    [c('user123'), c('user123'), '1', 'The seed member. Owns the 22 published sample announces (ids 2000001 and up), 3 drafts and 1 withdrawn one. Use it as the seller side.'],
    [c('sdcheck1792845801'), c('Descriptor-2026'), '61', 'Created on 2026-10-24. Owns the published announce %s and one draft. Seller in the chat test below.' % c('f16dcaf4-0e66-4bff-be59-a98b95a07442')],
    [c('exp21792849879'), c('Descriptor-2026'), '64', 'Created on 2026-10-25. Buyer who opened conversation %s with member 61.' % c('6523e1a7-3d2a-31c0-80c3-2c759a57b86c')],
    ['new', 'your choice, 8+ chars', '', 'Register at %s: username (3 to 63 chars of %s), password, optional nickname and mobile. The customer edge creates the row in %s.' % (c('/register'), c('A-Z a-z 0-9 . _ @ -'), c('announce_user'))],
], [3.6 * cm, 2.9 * cm, 0.9 * cm, 9.6 * cm]))
s.append(p('A member can only chat with another member about an announce, so every chat test needs two accounts in two browser '
           'sessions (a private window works). Member tokens last 2 hours and refresh through an httpOnly cookie.', NOTE))

s.append(p('3.2 Staff (admin SPA, %s)' % c('http://localhost:8091/login'), H3))
s.append(table([
    ['Username', 'Password', 'Role', 'Permissions today'],
    [c('admin123'), c('admin123'), '1 Super administrator', '%s: everything, including admins, roles and permissions.' % c('*')],
    [c('mall123'), c('mall123'), '2 Marketplace administrator', 'Logs in with %s. Meant for announces, catalogue, listings, members, regions.' % c('ROLE_ADMIN')],
    [c('promotion123'), c('promotion123'), '3 Support administrator', 'Logs in with %s. Meant for the support queue and bulletins.' % c('ROLE_ADMIN')],
], [3.0 * cm, 2.6 * cm, 4.2 * cm, 7.2 * cm]))
s.append(p('Permission codes are not enforced per route yet (worktree %s, steps 2 to 7 are still a guide, see '
           '%s): any enabled staff account can use every screen except the super-only ones (admins, roles, '
           'permissions). The seed roles 2 and 3 still carry litemall permission strings. Treat %s as the account for every '
           'staff test.' % (c('staff-permissions'), c('doc/staff-permissions-report.pdf'), c('admin123')), NOTE))

# ------------------------------------------------------------------ 4
s.append(PageBreak())
s.append(p('4. Walk-through: one pass over every service', H2))
s.append(p('Each block names the services it touches. Do them in order the first time; later you can jump to one.'))

s.append(p('4.1 Register and log in (customer edge)', H3))
s.append(steps([
    'Open %s, click "Register", create a member (call it the buyer). You are logged in afterwards; the header shows the username as a dropdown.' % c('http://localhost:8090/'),
    'Log out, log in as %s (the seller). "My announces" in the dropdown lists its 22 published announces and 3 drafts.' % c('user123'),
]))

s.append(p('4.2 Post, publish, search (announce-management, OCS)', H3))
s.append(steps([
    'As the seller click "Post" and pick a kind. Goods: title, category of the goods tree, price, condition, sale type (FIXED_PRICE, AUCTION or GIVEAWAY). Job: employer, contract type, deadline, how to apply; no sale type. Event: start before end, in the future; venue and organizer.',
    'Save: the announce is a DRAFT, visible only to you (%s answers 404 to anyone else). Open it from "My announces" and click "Publish" with a validity in days.' % c('/announce/{id}'),
    'Search for a word of the title at %s. With %s on, the search projection indexed it into OCS within a second or two; the facets show %s and %s. '
    'Crawled listings from dba.dk and tradera.com appear in the same results with a source badge; their click goes out through %s.'
    % (c('/search'), c('--search=ocs'), c('kind'), c('source'), c('/out/{id}')),
    'Open the announce as a visitor (log out or a private window): status PUBLISHED, the kind-specific fields, no "Edit" button.',
]))
s.append(p('Right now every crawled listing row is GONE (last crawl older than the 7-day grace), so no crawled hit shows up until '
           '%s runs and the file in %s is imported (announce-management does it on start and every poll).'
           % (c('crawler/run.sh'), c('~/announce_crawled_data')), NOTE))

s.append(p('4.3 Chat between two members (messaging, the shared announce descriptor)', H3))
s.append(steps([
    'As the buyer (second browser session) open a published announce of the seller, for example %s, and click "Message the seller". '
    'messaging asks announce-management for the announce over the shared %s descriptor and opens one conversation per announce and buyer; '
    'you land on %s.' % (c('http://localhost:8090/announce/f16dcaf4-0e66-4bff-be59-a98b95a07442'), c('AnnounceApi'), c('/messages/{id}')),
    'Write a message and send it. In the seller\'s session the "Messages" entry of the dropdown lists the conversation; open it and reply. Both sides update live over the WebSocket.',
    'As the buyer send an offer (the offer control in the composer, goods announces only). As the seller accept or decline it. An accepted offer publishes %s on %s; with the order module up it becomes an order.' % (c('OfferAcceptedV1'), c('messaging.v1')),
    'Clicking "Message the seller" again on the same announce reopens the same conversation. On your own announce it answers "buyer is seller". On a draft or unknown id it answers "announce not found".',
    'Support: in the inbox at %s the "Contact support" form opens a SUPPORT conversation that waits until staff joins (4.5).' % c('/messages'),
]))

s.append(p('4.4 Trade (order module, fake payment and carrier)', H3))
s.append(steps([
    'Fixed price: as the buyer open a FIXED_PRICE announce and request to buy; the seller accepts from "Orders"; the order is AGREED. '
    'Auction: publish an AUCTION announce; the order module opens the auction from %s; the buyer bids from the auction panel; a bid from the seller or below the increment is refused.' % c('announce.v1'),
    'Payment and shipping use fake providers. The webhooks %s and %s take the provider event as JSON with an HMAC-SHA256 hex signature of the raw body in %s '
    '(secrets %s and %s in the order configuration). The exact bodies and the full state machine are in %s and %s.'
    % (c('POST /api/payments/webhook/fake'), c('POST /api/shipping/webhook/fake'), c('X-Fake-Signature'), c('payment.fake.secret'),
       c('shipping.fake.secret'), c('doc/order-context.md'), c('doc/customer-trade-wiring-report.pdf')),
    'After the payment webhook the order is PAID and announce-management marks the announce SOLD (subscriber on %s). After carrier delivery and the grace period the payout is released and the order SETTLED. The "Orders" screen of both members and the admin "Orders" and "Payouts" lists follow along.' % c('order.v1'),
]))

s.append(p('4.5 Staff (admin edge)', H3))
s.append(steps([
    'Log in at %s as %s. The dashboard counts announces, crawled listings and members.' % (c('http://localhost:8091/login'), c('admin123')),
    'Announces: filter by kind, status, seller or text; withdraw or expire one with a reason. The member sees the new status at once; a withdrawn announce leaves the search index.',
    'Catalogue: categories per kind and brands. Members: search, open one, change its status. Regions.',
    'Crawled listings: trigger an import of a file path, a reindex, and read the inventory (per source, status, quality grade, dead links).',
    'Support queue at %s: the WAITING list shows support conversations nobody picked up; "Join" moves one to OPEN and lets you answer. '
    'Switch the status dropdown to "Open" to see listing conversations between members; %s opens any thread read-only (close or block it). '
    'The two test conversations are on the last page of "Open" because older load-test rows come first.' % (c('/support'), c('/support/{id}')),
    'Bulletins: post one to an audience; connected staff clients receive it over %s.' % c('/ws/staff'),
    'Auctions, Orders, Payouts: the trade lists over the order module; resolve a dispute from an order.',
]))

# ------------------------------------------------------------------ 5
s.append(p('5. Looking under the hood', H2))
s.append(bullets([
    'Logs: %s with one file per service; messaging logs every outbound HTTP call at TRACE when you add %s to its arguments.'
    % (c('~/.announce-stack/logs/'), c('--logging.level.org.springframework.web.reactive.function.client.ExchangeFunctions=TRACE')),
    'Events: the EventSourcingDB UI at %s (token %s) shows the subjects %s, %s, %s, %s, %s, %s.'
    % (c('http://localhost:3000/'), c('secret'), c('/announce/{id}'), c('/conversation/{id}'), c('/auction/{id}'), c('/order/{id}'), c('/payment/{id}'), c('/shipment/{id}')),
    'Read models: MySQL %s, tables %s, %s, %s, %s, %s, %s and the projection checkpoints in %s.'
    % (c('announce_reactive_db'), c('announce'), c('announce_goods_detail'), c('crawled_listing'), c('announce_conversation'), c('announce_chat_message'), c('announce_order'), c('cqrs_readside_progress')),
    'Direct backend calls need the service token. Easiest: call through an edge with a member or staff bearer token (%s then %s).'
    % (c('POST /auth/login'), c('Authorization: Bearer ...')),
]))

# ------------------------------------------------------------------ 6
s.append(p('6. Known quirks', H2))
s.append(bullets([
    'announce-management imports %s (and any %s / %s file) from %s on every start. It is idempotent; it just takes a few seconds and fills the log.'
    % (c('dba_cars.json'), c('dba_*.jsonl'), c('tradera_*.jsonl'), c('~/announce_crawled_data')),
    'Jars built before 2026-10-01 predate the service-token change and answer every proxied call with 401. Run %s after pulling.' % c('scripts/start-stack.sh --build'),
    'Port 8080 is held by another application on this machine, so the customer edge runs on 8090 and the admin edge on 8091 (its own default is 8081). Override with %s and %s.'
    % (c('PORT_CUSTOMER'), c('PORT_ADMIN')),
    'The messaging end-to-end test suite stalls while a messaging instance of the stack uses the live database: stop the stack before %s.' % c('mvn -pl messaging test'),
    'Headless browsers on this machine have no DNS, so external images in the SPA fail to load there; a desktop browser is fine.',
    'A dev EventSourcingDB that stops answering reads after broken replay streams: restart the container, wait for healthy, then start the services one by one (see %s).' % c('doc/messaging-tuning-so-far-report.pdf'),
]))

doc.build(s, onFirstPage=footer, onLaterPages=footer)
print('wrote', OUT)
