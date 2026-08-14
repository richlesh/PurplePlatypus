Below are minimal, working examples of **every** currently‑supported Mermaid 11.16 diagram type.  
Copy any block into a Mermaid‑enabled viewer (e.g., Markdown preview, MkDocs, Notion, GitHub, etc.) to see the rendered diagram.

---

## 1. Graph (`graph`)

<div style="text-align: center;">

```mermaid
graph TD
    A[Start] --> B{Decision}
    B -->|Yes| C[Result 1]
    B -->|No| D[Result 2]
```

</div>

---

## 2. Flowchart (`flowchart`)

<div style="text-align: center;">

```mermaid
flowchart LR
    Start --> Process[Do something] --> End[Finish]
```

</div>

---

## 3. Sequence Diagram (`sequenceDiagram`)

<div style="text-align: center;">

```mermaid
sequenceDiagram
    participant Alice
    participant Bob
    Alice->>Bob: Hello Bob
    Bob-->>Alice: Hi Alice
    Alice->>Bob: How are you?
    Bob-->>Alice: Great!
```

</div>

---

## 4. Class Diagram (`classDiagram`)

<div style="text-align: center;">

```mermaid
classDiagram
    Animal <|-- Dog
    Animal <|-- Cat

    class Animal {
        +String name
        +int age
        +void eat()
    }
    class Dog {
        +void bark()
    }
    class Cat {
        +void meow()
    }
```

</div>

---

## 5. State Diagram v2 (`stateDiagram-v2`)

<div style="text-align: center;">

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Running : start
    Running --> Idle : stop
    Running --> Error : crash
    Error --> [*]
```

</div>

---

## 6. ER Diagram (`erDiagram`)

<div style="text-align: center;">

```mermaid
erDiagram
    CUSTOMER ||--o{ ORDER : places
    ORDER ||--|{ LINE-ITEM : contains

    CUSTOMER {
        string name
        string custId
    }
    ORDER {
        int orderId
        date orderDate
    }
    LINE-ITEM {
        int quantity
        float price
    }
```

</div>

---

## 7. Gantt (`gantt`)

<div style="text-align: center;">

```mermaid
gantt
    title Project Timeline
    dateFormat  YYYY-MM-DD
    section Development
    Coding           :a1, 2023-01-01, 10d
    Testing          :after a1, 5d
    Review           :2023-01-20, 2d
```

</div>

---

## 8. Pie (`pie`)

<div style="text-align: center;">

```mermaid
pie
    title Pets Adopted
    "Dogs" : 45
    "Cats" : 30
    "Rabbits" : 15
    "Others" : 10
```

</div>
---

## 9. Mindmap (`mindmap`)

<div style="text-align: center;">

```mermaid
mindmap
    root((Mindmap))
        sub1[Idea 1]
            sub1a[Detail A]
            sub1b[Detail B]
        sub2[Idea 2]
            sub2a[Detail C]
```

</div>

---

## 10. Timeline (`timeline`)

<div style="text-align: center;">

```mermaid
timeline
    title History Timeline
    1990 : Event A
    2000 : Event B
    2010 : Event C
    2020 : Event D
```

</div>

---

## 11. GitGraph (`gitGraph`)

<div style="text-align: center;">

```mermaid
gitGraph
    commit
    branch develop
    checkout develop
    commit
    commit
    checkout main
    merge develop
```

</div>

---

## 12. Quadrant Chart (`quadrantChart`)

<div style="text-align: center;">

```mermaid
quadrantChart
    title Quadrant Chart Example
    x-axis Low --> High
    y-axis Low --> High
    "Item A": [0.30, 0.70]
    "Item B": [0.80, 0.20]
    "Item C": [0.50, 0.50]
```

</div>

<div style="text-align: center;">

```mermaid
quadrantChart
    title Product Priorities
    x-axis Low Effort --> High Effort
    y-axis Low Impact --> High Impact
    quadrant-1 Consider Carefully
    quadrant-2 Major Projects
    quadrant-3 Deprioritize
    quadrant-4 Quick Wins
    "Redesign homepage": [0.75, 0.85]
    "Fix typo": [0.15, 0.20]
    "Add dark mode": [0.45, 0.70]
    "Update dependencies": [0.30, 0.45]
```

</div>

---

## 13. XY Chart (`xychart-beta`)

<div style="text-align: center;">

```mermaid
xychart-beta
%%{init: {
  "themeVariables": {
    "xyChart": {
      "plotColorPalette": "#e63946,#457b9d"
    }
  }
}}%%
    title "Monthly Sales"
    x-axis ["Jan", "Feb", "Mar", "Apr", "May", "Jun"]
    y-axis "Sales" 0 --> 100
    line [35, 42, 50, 68, 75, 90]
    line [45, 52, 60, 78, 85, 100]
```

</div>

---

## 14. Block (`block-beta`)

<div style="text-align: center;">

```mermaid
block-beta
    a["Start"] --> b["Step 1"]
    b --> c["Decision"]
    c -->|yes| d["Step 2"]
    c -->|no| e["End"]
```

</div>

---

## 15. Sankey (`sankey-beta`)

<div style="text-align: center;">

```mermaid
sankey-beta
Agricultural 'waste',Bio-energy,124.729
Bio-energy,Electricity grid,35.0
Bio-energy,Losses,6.242
Bio-energy,Industry,10.606
```

</div>

---

## 16. Packet (`packet-beta`)

<div style="text-align: center;">

```mermaid
packet-beta
0-15: "Source Port"
16-31: "Destination Port"
32-63: "Sequence Number"
64-95: "Acknowledgment Number"
96-99: "Data Offset"
100-105: "Reserved"
106: "URG"
107: "ACK"
108: "PSH"
109: "RST"
110: "SYN"
111: "FIN"
112-127: "Window"
128-143: "Checksum"
144-159: "Urgent Pointer"
```

</div>

---

## 17. User Journey (`journey`)

<div style="text-align: center;">

```mermaid
journey
    title A User's Workday
    section Morning
        Make coffee: 5: User
        Check email: 4: User
    section Afternoon
        Attend meeting: 3: User
        Attend meeting: 3: Manager
        Complete tasks: 5: User
```

</div>

---

## 18. Requirement Diagram (`requirementDiagram`)

<div style="text-align: center;">

```mermaid
requirementDiagram
    requirement test_req {
        id: 1
        text: the test text
        risk: high
        verifymethod: test
    }
    element test_entity {
        type: simulation
    }
    test_entity - satisfies -> test_req
```

</div>

---

## 19. C4 Context Diagram (`C4Context`)

<div style="text-align: center;">

```mermaid
C4Context
    title System Context Diagram
    Person(user, "User", "A person using the application")
    System(app, "Application", "Main system")
    Rel(user, app, "Uses")
```

</div>

---

## 20. C4 Container Diagram (`C4Container`)

<div style="text-align: center;">

```mermaid
C4Container
    title Container Diagram
    Person(user, "User", "Application user")
    System_Boundary(app, "Application") {
        Container(web, "Web App", "JavaScript", "User interface")
        Container(api, "API", "Node.js", "Application services")
    }
    Rel(user, web, "Uses")
    Rel(web, api, "Calls")
```

</div>

---

## 21. C4 Component Diagram (`C4Component`)
```mermaid
C4Component
    title Component Diagram
    Container_Boundary(api, "API") {
        Component(auth, "Auth Component", "Service", "Handles authentication")
        Component(users, "User Component", "Service", "Manages users")
    }
    Rel(auth, users, "Authenticates")
```

---

## 22. C4 Dynamic Diagram (`C4Dynamic`)

<div style="text-align: center;">

```mermaid
C4Dynamic
    title Dynamic Diagram
    Person(user, "User", "Application user")
    Container(app, "Application", "Web app", "Main application")
    Rel(user, app, "1. Submits request")
    Rel(app, user, "2. Returns response")
```

</div>

---

## 23. C4 Deployment Diagram (`C4Deployment`)

<div style="text-align: center;">

```mermaid
C4Deployment
    title Deployment Diagram
    Deployment_Node(server, "Application Server", "Linux") {
        Container(app, "Application", "Node.js", "Main application")
    }
    Deployment_Node(database, "Database Server", "Linux") {
        ContainerDb(db, "Database", "PostgreSQL", "Stores application data")
    }
    Rel(app, db, "Reads and writes")
```

</div>

---

---

## 24. Kanban (`kanban`)

<div style="text-align: center;">

```mermaid
kanban
    Todo
        [Write documentation]
    In Progress
        [Build examples]
    Done
        [Choose diagram types]
```

</div>

---

## 25. Architecture (`architecture-beta`)

<div style="text-align: center;">

```mermaid
architecture-beta
    group app(cloud)[Application]
    service server(server)[Application Server] in app
    service db(database)[Database] in app
    server:R -- L:db
```

</div>

---

## 26. Radar Chart (`radar-beta`)

<div style="text-align: center;">

```mermaid
radar-beta
    title Team Skills
    axis Communication
    axis Design
    axis Coding
    axis Testing
    curve Team { 80, 70, 90, 75 }
```

</div>

---

## 27. Treemap (`treemap-beta`)

<div style="text-align: center;">

```mermaid
treemap-beta
    "Project"
        "Frontend"
            "Components": 40
            "Styles": 20
        "Backend"
            "API": 30
            "Database": 10
```

</div>

---

## 28. Venn Diagram (`venn-beta`)

<div style="text-align: center;">

```mermaid
venn-beta
    set A["Developers"] : 40
    set B["Designers"] : 30
    union A,B : 10
```

</div>

Feel free to adjust the contents or styling to suit your own documentation or presentation needs!